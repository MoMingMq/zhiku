package org.zhiku.ai.knowledge.vector;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.extern.slf4j.Slf4j;
import org.zhiku.common.core.exception.ServiceException;
import org.zhiku.common.core.utils.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Milvus 实现（SDK v2 API；collection=zhiku_kb_chunk_{dim}，kb_id 为 partition key）
 *
 * @author zhiku
 */
@Slf4j
public class MilvusKbVectorStore implements KbVectorStore {

    /** 字段名 */
    public static final String FIELD_ID = "id";
    public static final String FIELD_VECTOR = "vector";
    public static final String FIELD_KB_ID = "kb_id";
    public static final String FIELD_DOCUMENT_ID = "document_id";
    public static final String FIELD_CHUNK_ID = "chunk_id";

    private static final String COLLECTION_PREFIX = "zhiku_kb_chunk_";

    private final MilvusClientV2 client;
    private final String database;
    private final Gson gson = new Gson();

    public MilvusKbVectorStore(MilvusClientV2 client, String database) {
        this.client = client;
        this.database = database;
    }

    /**
     * 构建 Milvus v2 客户端
     */
    public static MilvusClientV2 createClient(MilvusProperties properties) {
        ConnectConfig.ConnectConfigBuilder builder = ConnectConfig.builder()
            .uri(properties.getUri())
            .dbName(properties.getDatabase());
        if (StringUtils.isNotBlank(properties.getToken())) {
            builder.token(properties.getToken());
        }
        return new MilvusClientV2(builder.build());
    }

    @Override
    public void ensureCollection(int dim) {
        String name = collection(dim);
        Boolean exists = client.hasCollection(HasCollectionReq.builder()
            .collectionName(name)
            .databaseName(database)
            .build());
        if (Boolean.TRUE.equals(exists)) {
            return;
        }
        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder().build()
            .addField(AddFieldReq.builder()
                .fieldName(FIELD_ID)
                .dataType(DataType.VarChar)
                .maxLength(32)
                .isPrimaryKey(true)
                .build())
            .addField(AddFieldReq.builder()
                .fieldName(FIELD_VECTOR)
                .dataType(DataType.FloatVector)
                .dimension(dim)
                .build())
            .addField(AddFieldReq.builder()
                .fieldName(FIELD_KB_ID)
                .dataType(DataType.Int64)
                .isPartitionKey(true)
                .build())
            .addField(AddFieldReq.builder()
                .fieldName(FIELD_DOCUMENT_ID)
                .dataType(DataType.Int64)
                .build())
            .addField(AddFieldReq.builder()
                .fieldName(FIELD_CHUNK_ID)
                .dataType(DataType.Int64)
                .build());
        IndexParam index = IndexParam.builder()
            .fieldName(FIELD_VECTOR)
            .indexType(IndexParam.IndexType.HNSW)
            .metricType(IndexParam.MetricType.COSINE)
            .extraParams(Map.of("M", 16, "efConstruction", 200))
            .build();
        client.createCollection(CreateCollectionReq.builder()
            .collectionName(name)
            .databaseName(database)
            .collectionSchema(schema)
            .indexParams(List.of(index))
            .build());
        log.info("Milvus collection 已创建：{}（dim={}）", name, dim);
    }

    @Override
    public void upsert(int dim, List<VectorRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<JsonObject> data = new ArrayList<>(rows.size());
        for (VectorRow row : rows) {
            JsonObject json = new JsonObject();
            json.addProperty(FIELD_ID, row.getId());
            json.add(FIELD_VECTOR, gson.toJsonTree(row.getVector()));
            json.addProperty(FIELD_KB_ID, row.getKbId());
            json.addProperty(FIELD_DOCUMENT_ID, row.getDocumentId());
            json.addProperty(FIELD_CHUNK_ID, row.getChunkId());
            data.add(json);
        }
        try {
            client.upsert(UpsertReq.builder()
                .collectionName(collection(dim))
                .databaseName(database)
                .data(data)
                .build());
        } catch (Exception e) {
            log.error("Milvus upsert 失败 dim={} rows={}", dim, rows.size(), e);
            throw new ServiceException("向量写入失败：" + e.getMessage());
        }
    }

    @Override
    public List<VectorHit> search(int dim, float[] queryVector, String filter, int topK) {
        try {
            SearchResp resp = client.search(SearchReq.builder()
                .collectionName(collection(dim))
                .databaseName(database)
                .data(List.of(new FloatVec(queryVector)))
                .filter(filter)
                .limit(topK)
                .outputFields(List.of(FIELD_KB_ID, FIELD_DOCUMENT_ID, FIELD_CHUNK_ID))
                .build());
            List<VectorHit> hits = new ArrayList<>();
            for (List<SearchResp.SearchResult> results : resp.getSearchResults()) {
                for (SearchResp.SearchResult result : results) {
                    VectorHit hit = new VectorHit();
                    Map<String, Object> entity = result.getEntity();
                    hit.setId(String.valueOf(entity.get(FIELD_ID)));
                    hit.setKbId(toLong(entity.get(FIELD_KB_ID)));
                    hit.setDocumentId(toLong(entity.get(FIELD_DOCUMENT_ID)));
                    hit.setChunkId(toLong(entity.get(FIELD_CHUNK_ID)));
                    hit.setScore(result.getScore());
                    hits.add(hit);
                }
            }
            return hits;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Milvus search 失败 dim={} filter={}", dim, filter, e);
            throw new ServiceException("向量服务不可用：" + e.getMessage());
        }
    }

    @Override
    public void deleteByExpr(int dim, String expr) {
        try {
            client.delete(DeleteReq.builder()
                .collectionName(collection(dim))
                .databaseName(database)
                .filter(expr)
                .build());
        } catch (Exception e) {
            log.error("Milvus delete 失败 dim={} expr={}", dim, expr, e);
            throw new ServiceException("向量删除失败：" + e.getMessage());
        }
    }

    @Override
    public long count(int dim, String filter) {
        try {
            QueryResp resp = client.query(QueryReq.builder()
                .collectionName(collection(dim))
                .databaseName(database)
                .filter(StringUtils.isBlank(filter) ? "" : filter)
                .outputFields(List.of("count(*)"))
                .build());
            for (QueryResp.QueryResult row : resp.getQueryResults()) {
                Object value = row.getEntity().get("count(*)");
                if (value instanceof Number number) {
                    return number.longValue();
                }
            }
            return 0;
        } catch (Exception e) {
            log.error("Milvus count 失败 dim={} filter={}", dim, filter, e);
            throw new ServiceException("向量计数失败：" + e.getMessage());
        }
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.parseLong(String.valueOf(value));
    }

    private String collection(int dim) {
        return COLLECTION_PREFIX + dim;
    }

}
