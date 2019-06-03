package com.gojek.daggers.longbow;

import com.gojek.daggers.longbow.metric.LongBowAspects;
import com.gojek.daggers.utils.stats.StatsManager;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.async.ResultFuture;
import org.apache.flink.streaming.api.functions.async.RichAsyncFunction;
import org.apache.flink.types.Row;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.util.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.threeten.bp.Duration;

import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import static com.gojek.daggers.Constants.*;
import static java.time.Duration.between;

public class LongBowWriter extends RichAsyncFunction<Row, Row> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LongBowWriter.class.getName());
    private static final byte[] COLUMN_FAMILY_NAME = Bytes.toBytes(LONGBOW_COLUMN_FAMILY_DEFAULT);

    private Instant startTime;
    private StatsManager statsManager;
    private Configuration configuration;
    private LongBowSchema longbowSchema;
    private String longbowDocumentDuration;
    private LongBowStore longBowStore;


    public LongBowWriter(Configuration configuration, LongBowSchema longbowSchema) {
        this.configuration = configuration;
        this.longbowSchema = longbowSchema;
        this.longbowDocumentDuration = configuration.getString(LONGBOW_DOCUMENT_DURATION, LONGBOW_DOCUMENT_DURATION_DEFAULT);
    }

    LongBowWriter(Configuration configuration, LongBowSchema longBowSchema, StatsManager statsManager, LongBowStore longBowStore) {
        this(configuration, longBowSchema);
        this.statsManager = statsManager;
        this.longBowStore = longBowStore;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        if (longBowStore == null)
            longBowStore = LongBowStore.create(configuration);

        if (statsManager == null)
            statsManager = new StatsManager(getRuntimeContext(), true);
        statsManager.register(longBowStore.groupName(), LongBowAspects.values());

        if (!longBowStore.tableExists()) {
            startTime = Instant.now();
            try {
                Duration maxAgeDuration = Duration.ofMillis(longbowSchema.getDurationInMillis(longbowDocumentDuration));
                String columnFamilyName = new String(COLUMN_FAMILY_NAME);
                longBowStore.createTable(maxAgeDuration, columnFamilyName);
                LOGGER.info("table '{}' is created with maxAge '{}' on column family '{}'", longBowStore.tableName(), maxAgeDuration, columnFamilyName);
                statsManager.markEvent(LongBowAspects.SUCCESS_ON_CREATE_BIGTABLE);
                statsManager.updateHistogram(LongBowAspects.SUCCESS_ON_CREATE_BIGTABLE_RESPONSE_TIME, between(startTime, Instant.now()).toMillis());
            } catch (Exception ex) {
                LOGGER.error("failed to create table '{}'", longBowStore.tableName());
                statsManager.markEvent(LongBowAspects.FAILURES_ON_CREATE_BIGTABLE);
                statsManager.updateHistogram(LongBowAspects.FAILURES_ON_CREATE_BIGTABLE_RESPONSE_TIME, between(startTime, Instant.now()).toMillis());
                throw ex;
            }
        }
        longBowStore.initialize();
    }

    @Override
    public void asyncInvoke(Row input, ResultFuture<Row> resultFuture) throws Exception {
        Put putRequest = new Put(longbowSchema.getKey(input, 0));
        longbowSchema
                .getColumns(c -> c.getKey().contains(LONGBOW_DATA))
                .forEach(column -> putRequest.addColumn(COLUMN_FAMILY_NAME, Bytes.toBytes(column), Bytes.toBytes((String) input.getField(longbowSchema.getIndex(column)))));
        startTime = Instant.now();
        CompletableFuture<Void> writeFuture = longBowStore.put(putRequest);
        writeFuture
                .exceptionally(this::logException)
                .thenAccept(aVoid -> {
                    resultFuture.complete(Collections.singleton(input));
                    statsManager.markEvent(LongBowAspects.SUCCESS_ON_WRITE_DOCUMENT);
                    statsManager.updateHistogram(LongBowAspects.SUCCESS_ON_WRITE_DOCUMENT_RESPONSE_TIME, between(startTime, Instant.now()).toMillis());
                });
    }

    private Void logException(Throwable ex) {
        LOGGER.error("failed to write document to table '{}'", longBowStore.tableName());
        ex.printStackTrace();
        statsManager.markEvent(LongBowAspects.FAILURES_ON_WRITE_DOCUMENT);
        statsManager.updateHistogram(LongBowAspects.FAILURES_ON_WRITE_DOCUMENT_RESPONSE_TIME, between(startTime, Instant.now()).toMillis());
        return null;
    }

    public void timeout(Row input, ResultFuture<Row> resultFuture) throws Exception {
        statsManager.markEvent(LongBowAspects.TIMEOUTS_ON_WRITER);
        resultFuture.complete(Collections.singleton(input));
    }
}
