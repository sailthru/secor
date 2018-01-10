package com.pinterest.secor.common;

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.serializers.KafkaAvroDecoder;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class SecorSchemaRegistryClient {

    private static final Logger LOG = LoggerFactory.getLogger(SecorSchemaRegistryClient.class);

    private static Object mutex = new Object();
    private KafkaAvroDecoder decoder;
    private static Map<String, Schema> schemas;

    private static volatile SecorSchemaRegistryClient instance = null;

    public SecorSchemaRegistryClient(SecorConfig config) {
        try {
            LOG.info("Initializing schema registry {}",  config.getSchemaRegistryUrl());
            Properties props = new Properties();
            props.put("schema.registry.url", config.getSchemaRegistryUrl());
            CachedSchemaRegistryClient schemaRegistryClient = new CachedSchemaRegistryClient(config.getSchemaRegistryUrl(), 30);
            decoder = new KafkaAvroDecoder(schemaRegistryClient);
            schemas = new ConcurrentHashMap<>();
        } catch (Exception e){
            LOG.error("Error initalizing schema registry", e);
            throw new RuntimeException(e);
        }
    }

    public GenericRecord decodeMessage(String topic, byte[] message) {
        GenericRecord record = (GenericRecord) decoder.fromBytes(message);
        Schema schema = record.getSchema();
        schemas.putIfAbsent(topic, schema);
        return record;
    }

    public Schema getSchema(String topic) {
        try {
            return schemas.get(topic);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    //    public static GenericRecord decodeMessage(Message message) {
//        GenericRecord record = (GenericRecord) decoder.fromBytes(message.getPayload());
//        schemas.put(message.getTopic(), record.getSchema());
//        return record;
//    }

}