package dulleh.akhyou.Utils;

import android.content.Intent;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.jsoup.Jsoup;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import de.greenrobot.event.EventBus;
import dulleh.akhyou.Models.Anime;
import dulleh.akhyou.Models.Providers;
import dulleh.akhyou.Models.SourceProviders.SourceProvider;
import dulleh.akhyou.Utils.Events.SnackbarEvent;
import okhttp3.Request;
import okhttp3.Response;
import rx.exceptions.OnErrorThrowable;

public class GeneralUtils {

    public static Response makeRequest (final Request request) throws OnErrorThrowable {
        try {
            return OK.INSTANCE.Client.newCall(request).execute();
        } catch (IOException io) {
            throw OnErrorThrowable.from(io);
        }
    }

    public static String getWebPage (final String url) {
        return GeneralUtils.getWebPage(new Request.Builder().url(url).build());
    }

    public static String getWebPage (final Request request) {
        try {
            return GeneralUtils.makeRequest(request).body().string();
        } catch (IOException e) {
            throw OnErrorThrowable.from(new Throwable("Failed to connect.", e));
        }
    }

    public static String encodeForUtf8 (String s) {
        try {
            return URLEncoder.encode(s, "utf-8");
        } catch (UnsupportedEncodingException u) {
            u.printStackTrace();
            return s.replace(":", "%3A")
                    .replace("/", "%2F")
                    .replace("#", "%23")
                    .replace("?", "%3F")
                    .replace("&", "%24")
                    .replace("@", "%40")
                    .replace("%", "%25")
                    .replace("+", "%2B")
                    .replace(" ", "+")
                    .replace(";","%3B")
                    .replace("=", "%3D")
                    .replace("$", "%26")
                    .replace(",", "%2C")
                    .replace("<", "%3C")
                    .replace(">", "%3E")
                    .replace("~", "%25")
                    .replace("^", "%5E")
                    .replace("`", "%60")
                    .replace("\\", "%5C")
                    .replace("[", "%5B")
                    .replace("]", "%5D")
                    .replace("{", "%7B")
                    .replace("|", "%7C")
                    .replace("\"", "%22");
        }
    }

    public static void lazyDownload(AppCompatActivity activity,  String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        if (intent.resolveActivity(activity.getPackageManager()) != null) {
            activity.startActivity(intent);
        } else {
            EventBus.getDefault().post(new SnackbarEvent("No app to open this link."));
        }
    }

    public static String formatError (Throwable e) {
        if (e.getMessage() != null) {
            return "Error: " + e.getMessage().replace("java.lang.Throwable:", "").trim();
        }
        return "An error occurred.";
    }

    public static String jwPlayerIsolate (String body) {
        String javascriptShit = Jsoup.parse(body).select("div#player_code").first().child(0).html();

        String almostVideoURL = javascriptShit.substring(javascriptShit.indexOf("\"file\": \"") + 9);

        return almostVideoURL.substring(0, almostVideoURL.indexOf("\","));
    }

    public static String formattedGenres(String[] genres) {
        StringBuilder genresBuilder = new StringBuilder();
        for (String genre : genres) {
            genresBuilder.append(" ");
            genresBuilder.append(genre);
            genresBuilder.append(",");
        }
        genresBuilder.deleteCharAt(genresBuilder.length() - 1);
        return genresBuilder.toString();
    }

    @Nullable
    public static String serializeAnime(Anime anime) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.writeValueAsString(anime);
        }catch (IOException io) {
            io.printStackTrace();
            return null;
        }
    }

    @Nullable
    public static Anime deserializeAnime(String serializedFavourite) {
        try {
            return new ObjectMapper().readValue(serializedFavourite, Anime.class);
        }catch (IOException io) {
            io.printStackTrace();
            return null;
        }
    }

    public static int determineProviderType (String url) throws Exception{
        url = url.toUpperCase();
        if (url.contains(Providers.RUSH_TITLE)) {
            return Providers.RUSH;
        } else if (url.contains(Providers.RAM_TITLE)) {
            return Providers.RAM;
        } else if (url.contains(Providers.BAM_TITLE)) {
            return Providers.BAM;
        }
        throw new Exception("Unsupported source");
    }


    public static SourceProvider determineSourceProvider (String lowerCaseTitle) {
        for (String sourceName : Providers.SOURCE_MAP.keySet()) {
            if (lowerCaseTitle.contains(sourceName)) {
                return Providers.SOURCE_MAP.get(sourceName);
            }
        }
        return null;
    }

}










