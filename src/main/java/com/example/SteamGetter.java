package com.example;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.*;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;

import static org.postgresql.core.Oid.JSON;

public class SteamGetter {
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static String DB_URL;
    private static String DB_USER;
    private static String DB_PASSWORD;
    private static String API_ADDRESS = "https://store.steampowered.com/api/appdetails?appids=";
    private static String MYSQL_URL;
    private static String MYSQL_USER;
    private static String MYSQL_PASSWORD;
    static {
        try (InputStream input = SteamGetter.class.getClassLoader().getResourceAsStream("application.properties")) {
            Properties prop = new Properties();
            prop.load(input);
            DB_URL = prop.getProperty("db.url");
            DB_USER = prop.getProperty("db.user");
            DB_PASSWORD = prop.getProperty("db.password");
            MYSQL_URL = prop.getProperty("mysql.url");
            MYSQL_USER = prop.getProperty("mysql.user");
            MYSQL_PASSWORD = prop.getProperty("mysql.password");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean isKorean(String text) {
        return text.matches(".*[ㄱ-ㅎㅏ-ㅣ가-힣]+.*"); // 한글 포함 여부 체크
    }

    public static Connection connect() {
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            System.out.println("✅ PostgreSQL 연결 성공!");
        } catch (Exception e) {
            System.out.println("❌ PostgreSQL 연결 실패: " + e.getMessage());
        }
        return conn;
    }

    public static Connection mysqlConnect() {
        Connection conn = null;
        int retryCount = 0;
        while (conn == null && retryCount < 5) { // 최대 5번 재시도
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
                conn = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD);
                System.out.println("✅ MySQL 연결 성공!");
            } catch (Exception e) {
                System.out.println("❌ MySQL 연결 실패: " + e.getMessage());
                retryCount++;
                if (retryCount < 5) {
                    System.out.println("🔄 5초 후 재시도... (" + retryCount + "/5)");
                    try {
                        Thread.sleep(5000); // 5초 대기 후 재시도
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    System.out.println("🚨 MySQL 연결을 5회 시도했으나 실패하였습니다. 프로그램 종료.");
                    return null;
                }
            }
        }
        return conn;
    }

    private static void sendDiscordAlert(String message) {
        OkHttpClient client = new OkHttpClient();
        MediaType json = MediaType.get("application/json; charset=utf-8");
        String jsonPayload = "{ \"content\": \"" + message + "\" }";
        RequestBody body = RequestBody.create(jsonPayload, json);
        Request request = new Request.Builder()
                .url("https://discord.com/api/webhooks/1344584212908605542/ZKv14TY5FNqurYag7I9UKJekNgrIjyd58b0E-3h8zRcCBn0RsuaSZbAtMHT-aZjQ2u7J")
                .post(body)
                .build();

        System.exit(0);
        try (Response response = client.newCall(request).execute()) {
            System.out.println("✅ Discord Webhook 응답 코드: " + response.code());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String jsonArrayToString(JsonArray jsonArray) {
        if (jsonArray == null || jsonArray.size() == 0) {
            return "N/A"; // 빈 배열이면 "N/A" 반환
        }
        List<String> list = new ArrayList<>();
        jsonArray.forEach(element -> list.add(element.getAsJsonObject().get("description").getAsString())); // 각 객체에서 description 가져오기
        return String.join(", ", list); // 쉼표로 연결된 문자열 반환
    }

    private static String fetchApiData(String url, String gameId) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(url).get().build();

        try (Response response = client.newCall(request).execute()) {
            int statusCode = response.code();
            if (statusCode == 403) {
                System.out.println("🚨 경고: Steam에서 차단되었습니다! (403 Forbidden)");
                sendDiscordAlert("🚨 Steam API에서 차단됨! (403 Forbidden)");
                System.exit(1);
            } else if (statusCode == 429) {
                System.out.println("⏳ 요청 한도 초과! (429 Too Many Requests) - 일정 시간 후 재시도");
                Thread.sleep(60000); // 1분 대기 후 다시 요청 (Steam API Rate Limit 대응)
                return fetchApiData(url, gameId);
            } else if (!response.isSuccessful() || response.body() == null) {
                System.out.println("❌ Steam API 응답 실패: " + statusCode);
                return null;
            }
            if (response.isSuccessful() && response.body() != null) {
                JsonObject jsonObject = JsonParser.parseString(response.body().string()).getAsJsonObject();
                if(!jsonObject.getAsJsonObject(gameId).get("success").getAsBoolean()){
                    return null;
                }
                JsonObject data = jsonObject.getAsJsonObject(gameId).getAsJsonObject("data");
                Gson gson = new Gson();
                JsonObject newJson = new JsonObject();
                newJson.addProperty("steam_appid", data.get("steam_appid").getAsInt());
                newJson.addProperty("name", data.get("name").getAsString());
                String description = data.get("short_description").getAsString();
                if(!isKorean(description)){
                    return null;
                }
                newJson.addProperty("short_description", description);
                newJson.addProperty("header_image", data.get("header_image").getAsString());
                newJson.addProperty("categories",
                        data.has("categories") && data.get("categories").isJsonArray()
                                ? jsonArrayToString(data.getAsJsonArray("categories"))
                                : "N/A"
                );
                newJson.addProperty("developers",
                        data.has("developers") && data.get("developers").isJsonArray()
                                ? data.getAsJsonArray("developers").asList().stream().map(e -> e.getAsString()).collect(Collectors.joining(", "))
                                : "N/A"
                );

                newJson.addProperty("publishers",
                        data.has("publishers") && data.get("publishers").isJsonArray()
                                ? data.getAsJsonArray("publishers").asList().stream().map(e -> e.getAsString()).collect(Collectors.joining(", "))
                                : "N/A"
                );

                if (data.has("price_overview")) {
                    JsonObject priceOverview = data.getAsJsonObject("price_overview");
                    newJson.addProperty("price", (priceOverview.get("initial").getAsInt())/100);
                    newJson.addProperty("discountedPrice", (priceOverview.get("final").getAsInt())/100);
                } else {
                    newJson.addProperty("price", 0);
                    newJson.addProperty("discountedPrice", 0);
                }

                // ✅ 출시일 정보가 존재하는 경우만 처리
                if (data.has("release_date")) {
                    newJson.addProperty("release_date", data.getAsJsonObject("release_date").get("date").getAsString());
                } else {
                    newJson.addProperty("release_date", "N/A");
                }

                String result = gson.toJson(newJson);
                return result;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static void insertIntoDatabase(Connection conn, int gameId, String gameInfoJson) {
        String sql = "INSERT INTO ORIGINAL_GAME_DATA (game_id, game_data, update_at) " +
                "VALUES (?, ?::jsonb, CURRENT_TIMESTAMP) " +
                "ON CONFLICT (game_id) DO UPDATE " +
                "SET game_data = EXCLUDED.game_data, " +
                "update_at = CURRENT_TIMESTAMP";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, gameId);
            pstmt.setString(2, gameInfoJson);
            pstmt.executeUpdate();
            System.out.println("✅ 게임 정보 저장 완료: Game ID = " + gameId);
        } catch (SQLException e) {
            System.out.println("❌ 게임 정보 저장 실패: " + e.getMessage());
        }
    }

    private static boolean isValidJson(String json) {
        try {
            JsonParser.parseString(json);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void main(String[] args) {
        Connection conn = connect();
        Connection mysqlConn = mysqlConnect();
        try {
            while(true) {
                String game = "";
                try (Statement stmt = mysqlConn.createStatement()) {
                    mysqlConn.setAutoCommit(false); // 트랜잭션 시작

                    // 첫 번째 행 가져오기 (잠금)
                    ResultSet rs = stmt.executeQuery("SELECT id, value FROM steam_data ORDER BY id LIMIT 1 FOR UPDATE");
                    if (rs.next()) {
                        int id = rs.getInt("id");
                        game = rs.getString("value"); // 게임 ID 가져오기

                        // 해당 행 삭제 (읽은 후 제거)
                        PreparedStatement deleteStmt = mysqlConn.prepareStatement("DELETE FROM steam_data WHERE id = ?");
                        deleteStmt.setInt(1, id);
                        deleteStmt.executeUpdate();
                        deleteStmt.close();

                        mysqlConn.commit(); // 트랜잭션 커밋
                        System.out.println("✅ 가져온 게임 ID: " + game);

                        // Steam API 요청
                        String address = API_ADDRESS + game + "&l=korean";
                        String result = fetchApiData(address, game);

                        if (result != null && isValidJson(result)) {
                            insertIntoDatabase(conn, Integer.parseInt(game), result);
                        } else {
                            System.out.println("❌ 유효하지 않은 데이터 스킵");
                        }
                    } else {
                        System.out.println("⚠️ 테이블에 읽을 데이터가 없습니다.");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    try {
                        mysqlConn.rollback(); // 오류 발생 시 롤백
                    } catch (SQLException rollbackEx) {
                        rollbackEx.printStackTrace();
                    }
                }
                if (game == null) break;

                String address = API_ADDRESS + game + "&l=korean";

                // DB 연결이 유효한지 확인 후 재연결
                if (conn == null || !conn.isValid(5)) {
                    System.out.println("🔄 DB 연결이 끊겼습니다. 다시 연결 시도...");
                    conn = connect();
                }

                try {
                    // 1초 대기 후 다음 요청
                    synchronized (SteamGetter.class) {
                        Thread.sleep(1000);
                    }
                    String result = fetchApiData(address, game);
                    System.out.println("📢 API 응답 데이터: " + result); // API 응답 확인

                    if (result == null) {
                        System.out.println("❌ 게임 데이터를 가져오지 못했습니다: Game ID = " + game);
                        continue;
                    }

                    // JSON 형식 검증
                    if (!isValidJson(result)) {
                        System.out.println("❌ 유효하지 않은 JSON 형식: " + result);
                        continue;
                    }

                    insertIntoDatabase(conn, Integer.parseInt(game), result);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                conn.close();
                scheduler.shutdown();
                System.out.println("DB closed");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
