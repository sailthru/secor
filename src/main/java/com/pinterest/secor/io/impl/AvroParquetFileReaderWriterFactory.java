package com.pinterest.secor.io.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import com.google.protobuf.Message;
import com.pinterest.secor.common.SecorSchemaRegistryClient;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.compress.CompressionCodec;
import org.apache.parquet.avro.AvroParquetReader;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;

import com.pinterest.secor.common.LogFilePath;
import com.pinterest.secor.common.SecorConfig;
import com.pinterest.secor.io.FileReader;
import com.pinterest.secor.io.FileReaderWriterFactory;
import com.pinterest.secor.io.FileWriter;
import com.pinterest.secor.io.KeyValue;
import com.pinterest.secor.util.ParquetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Implementation for reading/writing avro messages to/from Parquet files.
 *
 * @author Michael Spector (spektom@gmail.com)
 */
public class AvroParquetFileReaderWriterFactory implements FileReaderWriterFactory {

    private static final Logger LOG = LoggerFactory.getLogger(AvroParquetFileReaderWriterFactory.class);
    protected final int blockSize;
    protected final int pageSize;
    protected final boolean enableDictionary;
    protected final boolean validating;
    protected final SecorSchemaRegistryClient schemaRegistryClient;

    public AvroParquetFileReaderWriterFactory(SecorConfig config) {
        blockSize = ParquetUtil.getParquetBlockSize(config);
        pageSize = ParquetUtil.getParquetPageSize(config);
        enableDictionary = ParquetUtil.getParquetEnableDictionary(config);
        validating = ParquetUtil.getParquetValidation(config);
        schemaRegistryClient = new SecorSchemaRegistryClient(config);
    }

    @Override
    public FileReader BuildFileReader(LogFilePath logFilePath, CompressionCodec codec) throws Exception {
        return new AvroParquetFileReader(logFilePath, codec);
    }

    @Override
    public FileWriter BuildFileWriter(LogFilePath logFilePath, CompressionCodec codec) throws Exception {
        return new AvroParquetFileWriter(logFilePath, codec);
    }

    protected class AvroParquetFileReader implements FileReader {

        private ParquetReader<GenericRecord> reader;
        private SpecificDatumWriter<GenericRecord> writer;
        private long offset;

        public AvroParquetFileReader(LogFilePath logFilePath, CompressionCodec codec) throws IOException {
            Path path = new Path(logFilePath.getLogFilePath());
            String topic = logFilePath.getTopic();
            Schema schema = schemaRegistryClient.getSchema(topic);
            reader = AvroParquetReader.<GenericRecord>builder(path).build();
            writer = new SpecificDatumWriter(schema);
            offset = logFilePath.getOffset();
        }

        @Override
        public KeyValue next() throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Encoder encoder = EncoderFactory.get().directBinaryEncoder(out, null);
            GenericRecord record = reader.read();
            if (record != null) {
                writer.write(record, encoder);
                encoder.flush();
                ByteBuffer serialized = ByteBuffer.allocate(out.toByteArray().length);
                serialized.put(out.toByteArray());
                return new KeyValue(offset++, serialized.array());
            }
            return null;
        }

        @Override
        public void close() throws IOException {
            reader.close();
        }
    }

    protected class AvroParquetFileWriter implements FileWriter {

        private ParquetWriter writer;
        private String topic;

        public AvroParquetFileWriter(LogFilePath logFilePath, CompressionCodec codec) throws IOException {
            Path path = new Path(logFilePath.getLogFilePath());
            LOG.info("Creating Brand new Writer for path {}", path);
            topic = logFilePath.getTopic();
            // Not setting blockSize, pageSize, enableDictionary, and validating
            writer = AvroParquetWriter.builder(path)
                    .withSchema(schemaRegistryClient.getSchema(topic))
                    .withCompressionCodec(CompressionCodecName.GZIP)
                    .build();
        }

        @Override
        public long getLength() throws IOException {
            return writer.getDataSize();
        }

        @Override
        public void write(KeyValue keyValue) throws IOException {
            //GenericRecord record = avroUtil.decodeMessage(keyValue.getValue());
            GenericRecord record = schemaRegistryClient.decodeMessage(topic, keyValue.getValue());
            LOG.trace("Writing record {}", record);
            if (record != null){
                writer.write(record);
            }
        }

        @Override
        public void close() throws IOException {
            writer.close();
        }
    }
}
