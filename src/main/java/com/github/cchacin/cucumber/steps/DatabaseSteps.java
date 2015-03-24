/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.cchacin.cucumber.steps;

import com.ninja_squad.dbsetup.DbSetup;
import com.ninja_squad.dbsetup.destination.Destination;
import com.ninja_squad.dbsetup.destination.DriverManagerDestination;
import com.ninja_squad.dbsetup.operation.Insert;
import com.ninja_squad.dbsetup.operation.Operation;
import cucumber.api.DataTable;
import cucumber.api.java.en.Given;
import gherkin.formatter.model.DataTableRow;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static com.ninja_squad.dbsetup.Operations.deleteAllFrom;
import static com.ninja_squad.dbsetup.operation.CompositeOperation.sequenceOf;

public class DatabaseSteps {

    private static final Properties properties = new Properties();

    static {
        try (InputStream resource = Thread.currentThread()
                .getContextClassLoader().getResourceAsStream("test-db.properties")) {
            properties.load(resource);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private final Destination destination = new DriverManagerDestination(
            properties.getProperty("database.url"),
            properties.getProperty("database.user"),
            properties.getProperty("database.password"));

    @Given("^I have the following rows in the \"(.*?)\" table:$")
    public void i_have_the_following_rows_in_the_table(final String tableName,
                                                       final DataTable data) throws Throwable {
        this.insert(tableName, data);
    }

    @Given("^I have only the following rows in the \"([^\"]*)\" table:$")
    public void I_have_only_the_following_rows_in_the_table(
            final String tableName, final DataTable data) throws Throwable {
        this.deleteAll(tableName);
        this.insert(tableName, data);
    }

    void insert(final String tableName, final DataTable data) {
        final List<DataTableRow> rows = data.getGherkinRows();
        final List<String> columns = rows.get(0).getCells();

        final List<Operation> operations = new ArrayList<>();

        for (DataTableRow row : rows.subList(1, rows.size())) {
            final Insert.Builder builder = Insert.into(tableName);
            builder.columns(columns.toArray(new String[columns.size()]));
            builder.values(row.getCells().toArray(
                    new String[row.getCells().size()]));
            operations.add(builder.build());
        }

        this.apply(sequenceOf(operations));
    }

    void deleteAll(final String tableName) {
        this.apply(deleteAllFrom(tableName));
    }

    void apply(final Operation operation) {
        new DbSetup(destination, operation).launch();
    }

    @Given("^I have the following sql script \"([^\"]*)\"$")
    public void I_have_the_following_sql_script(final String script) throws Throwable {

        final URL resource = Thread.currentThread()
                .getContextClassLoader().getResource(script);
        if (resource == null) {
            return;
        }
        List<String> lines = FileUtils.readLines(new File(resource.toURI()), "UTF-8");
        final Connection connection = this.destination.getConnection();
        for (final String line : lines) {
            System.out.println(line);
            final CallableStatement sql = connection.prepareCall(line);
            sql.execute();
        }
    }
}