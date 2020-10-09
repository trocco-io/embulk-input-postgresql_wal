package org.embulk.input.postgres_wal;

import com.google.gson.JsonElement;
import org.embulk.spi.Column;
import org.embulk.spi.ColumnConfig;
import org.embulk.spi.ColumnVisitor;
import org.embulk.spi.PageBuilder;
import org.embulk.spi.json.JsonParser;
import org.embulk.spi.time.Timestamp;
import org.embulk.spi.time.TimestampParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class PostgresWalColumnVisitor implements ColumnVisitor {
    // timestamp pattern?
    private static final String DEFAULT_TIMESTAMP_PATTERN = "%Y-%m-%d %H:%M:%S";
    private final Logger logger = LoggerFactory.getLogger(PostgresWalColumnVisitor.class);

    private final PageBuilder pageBuilder;
    private final PluginTask pluginTask;
    private final PostgresWalAccessor accessor;

    public PostgresWalColumnVisitor(final PostgresWalAccessor accessor, final PageBuilder pageBuilder, final PluginTask pluginTask) {
        this.accessor = accessor;
        this.pageBuilder = pageBuilder;
        this.pluginTask = pluginTask;
    }

    @Override
    public void stringColumn(Column column) {
        try {
            String data = accessor.get(column.getName());
            if (Objects.isNull(data)) {
                pageBuilder.setNull(column);
            } else {
                pageBuilder.setString(column, data);
            }
        } catch (Exception e) {
            pageBuilder.setNull(column);
        }
    }

    @Override
    public void booleanColumn(Column column) {
        try {
            String data = accessor.get(column.getName());
            pageBuilder.setBoolean(column, Boolean.parseBoolean(data));
        } catch (Exception e) {
            System.out.println(e.getMessage());
            pageBuilder.setNull(column);
        }
    }

    @Override
    public void longColumn(Column column) {
        try {
            String data = accessor.get(column.getName());
            pageBuilder.setLong(column, Long.parseLong(data));
        } catch (Exception e) {
            pageBuilder.setNull(column);
        }
    }

    @Override
    public void doubleColumn(Column column) {
        try {
            String data = accessor.get(column.getName());
            pageBuilder.setDouble(column, Double.parseDouble(data));
        } catch (Exception e) {
            pageBuilder.setNull(column);
        }
    }

    @Override
    public void timestampColumn(Column column) {
        try {
            Timestamp result;
            // meta_fetched_at need microsecond
            if (column.getName().equals(PostgresWalUtil.getFetchedAtName(this.pluginTask))){
                result = Timestamp.ofInstant(Instant.now());
            }else {
                List<ColumnConfig> columnConfigs = pluginTask.getColumns().getColumns();
                String pattern = DEFAULT_TIMESTAMP_PATTERN;
                for (ColumnConfig config : columnConfigs) {
                    if (config.getName().equals(column.getName())
                            && config.getConfigSource() != null
                            && config.getConfigSource().getObjectNode() != null
                            && config.getConfigSource().getObjectNode().get("format") != null
                            && config.getConfigSource().getObjectNode().get("format").isTextual()) {
                        pattern = config.getConfigSource().getObjectNode().get("format").asText();
                        break;
                    }
                }
                TimestampParser parser = TimestampParser.of(pattern, pluginTask.getDefaultTimezone());
                result = parser.parse(accessor.get(column.getName()));
            }
            pageBuilder.setTimestamp(column, result);
        } catch (Exception e) {
            pageBuilder.setNull(column);
        }
    }

    @Override
    public void jsonColumn(Column column) {
        try {
            JsonElement data = new com.google.gson.JsonParser().parse(accessor.get(column.getName()));
            if (data.isJsonNull() || data.isJsonPrimitive()) {
                pageBuilder.setNull(column);
            } else {
                pageBuilder.setJson(column, new JsonParser().parse(data.toString()));
            }
        } catch (Exception e) {
            pageBuilder.setNull(column);
        }
    }
}
