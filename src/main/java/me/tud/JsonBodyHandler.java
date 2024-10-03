package me.tud;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.net.http.HttpResponse;
import java.nio.charset.Charset;

public class JsonBodyHandler implements HttpResponse.BodyHandler<JsonElement> {

    @Override
    public HttpResponse.BodySubscriber<JsonElement> apply(HttpResponse.ResponseInfo responseInfo) {
        HttpResponse.BodySubscriber<String> upstream = HttpResponse.BodySubscribers.ofString(Charset.defaultCharset());
        return HttpResponse.BodySubscribers.mapping(upstream, JsonParser::parseString);
    }

}
