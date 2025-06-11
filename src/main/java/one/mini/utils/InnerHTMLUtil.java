package one.mini.utils;

public class InnerHTMLUtil {

    public static String textResponse(String content) {
        String responseFormat = """
                        HTTP/1.1 200 OK\r
                        Content-Type: text/plain\r
                        \r
                        %s
                        """;
        return String.format(responseFormat, content);
    }

    public static String htmlResponse(String content) {
        String responseFormat = """
                        HTTP/1.1 200 OK\r
                        Content-Type: text/html\r
                        \r
                        %s
                        """;
        return String.format(responseFormat, content);
    }

}