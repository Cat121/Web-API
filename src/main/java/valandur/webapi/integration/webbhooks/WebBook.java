package valandur.webapi.integration.webbhooks;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import valandur.webapi.api.serialize.JsonDetails;

import java.util.List;

@JsonDeserialize
@JsonIgnoreProperties(ignoreUnknown = true)
public class WebBook {

    @JsonDeserialize
    private String id;
    public String getId() {
        return id;
    }

    @JsonDeserialize
    private String title;
    public String getTitle() {
        return title;
    }

    @JsonDeserialize
    private List<String> lines;
    public List<String> getLines() {
        return lines;
    }


    public WebBook() {
    }
    public WebBook(String id, String title, List<String> lines) {
        this.id = id;
        this.title = title;
        this.lines = lines;
    }

    @JsonDetails
    public String getHtml() {
        StringBuilder html = new StringBuilder("<!DOCTYPE><html><head><title>" + title + "</title></head><body><ul class='book'>");
        for (String line : lines) {
            html.append("<li>").append(line).append("</li>");
        }
        return html + "</ul></body></html>";
    }
}
