package com.knot.backend.workspace.infrastructure.notion;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.hibernate.resource.jdbc.spi.StatementInspector;

public class NotionPageQueryStatementInspector implements StatementInspector {
    private static final List<String> SQL_STATEMENTS = new CopyOnWriteArrayList<>();

    @Override
    public String inspect(String sql) {
        SQL_STATEMENTS.add(sql);
        return sql;
    }

    static void clear() {
        SQL_STATEMENTS.clear();
    }

    static List<String> selectsFromNotionPages() {
        return SQL_STATEMENTS.stream()
                .filter(
                        sql -> sql.toLowerCase()
                                .contains("select")
                                && sql.toLowerCase()
                                        .contains("notion_pages")
                )
                .toList();
    }
}
