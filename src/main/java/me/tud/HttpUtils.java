package me.tud;

import com.google.gson.JsonElement;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class HttpUtils {

    public static HttpResponse<JsonElement> sendRequest(HttpRequest request) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        return client.send(request, new JsonBodyHandler());
    }

}
