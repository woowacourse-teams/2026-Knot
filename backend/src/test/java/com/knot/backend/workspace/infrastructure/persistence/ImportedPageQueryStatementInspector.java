package com.knot.backend.workspace.infrastructure.persistence;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.hibernate.resource.jdbc.spi.StatementInspector;

public class ImportedPageQueryStatementInspector implements StatementInspector {
    private static final List<String> SQL_STATEMENTS = new CopyOnWriteArrayList<>();

    @Override
    public String inspect(String sql) {
        SQL_STATEMENTS.add(sql);
        return sql;
    }

    static void clear() {
        SQL_STATEMENTS.clear();
    }

    static List<String> selectsFromImportedPages() {
        return SQL_STATEMENTS.stream()
                .filter(
                        sql -> sql.toLowerCase()
                                .contains("select")
                                && sql.toLowerCase()
                                        .contains("imported_pages")
                )
                .toList();
    }
}
