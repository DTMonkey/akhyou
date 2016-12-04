package dulleh.akhyou.Anime;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.graphics.Palette;
import android.view.View;

import java.util.List;

import de.greenrobot.event.EventBus;
import dulleh.akhyou.MainModel;
import dulleh.akhyou.Models.AnimeProviders.BamAnimeProvider;
import dulleh.akhyou.Models.AnimeProviders.KissAnimeProvider;
import dulleh.akhyou.Models.AnimeProviders.RamAnimeProvider;
import dulleh.akhyou.Models.AnimeProviders.RushAnimeProvider;
import dulleh.akhyou.Models.AnimeProviders.AnimeProvider;
import dulleh.akhyou.Models.Anime;
import dulleh.akhyou.Models.Providers;
import dulleh.akhyou.Models.Source;
import dulleh.akhyou.R;
import dulleh.akhyou.Utils.AdapterDataHandler;
import dulleh.akhyou.Settings.SettingsFragment;
import dulleh.akhyou.Utils.CloudFlareInitializationException;
import dulleh.akhyou.Utils.Events.FavouriteEvent;
import dulleh.akhyou.Utils.Events.LastAnimeEvent;
import dulleh.akhyou.Utils.Events.OpenAnimeEvent;
import dulleh.akhyou.Utils.Events.SnackbarEvent;
import dulleh.akhyou.Utils.GeneralUtils;
import nucleus.presenter.RxPresenter;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func0;
import rx.schedulers.Schedulers;

public class AnimePresenter extends RxPresenter<AnimeFragment> implements AdapterDataHandler<Anime>{
    private static final String LAST_ANIME_BUNDLE_KEY = "last_anime";

    private Subscription animeSubscription;
    private Subscription episodeSubscription;
    private Subscription videoSubscription;
    private AnimeProvider animeProvider;

    private Anime lastAnime;
    public boolean isRefreshing;
    private static boolean needToGiveFavouriteState = false;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        if (savedState != null && savedState.containsKey(LAST_ANIME_BUNDLE_KEY)) {
            lastAnime = savedState.getParcelable(LAST_ANIME_BUNDLE_KEY);
        }
    }

    @Override
    protected void onTakeView(AnimeFragment view) {
        super.onTakeView(view);
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().registerSticky(this);
        }

        view.updateRefreshing();

        if (lastAnime != null && lastAnime.getUrl() != null) {

            if (animeProvider == null) setAnimeProvider(lastAnime);

            if (lastAnime.getEpisodes() != null) {
                view.setAnime(lastAnime);
            } else if (lastAnime.getTitle() != null) {
                view.setToolbarTitle(lastAnime.getTitle());
            }

            if (needToGiveFavouriteState) {
                view.setFavouriteChecked(view.isInFavourites(lastAnime));
                needToGiveFavouriteState = false;
            }

        }
    }

    @Override
    protected void onSave(Bundle state) {
        super.onSave(state);
        if (lastAnime != null && lastAnime.getEpisodes() != null) {
            state.putParcelable(LAST_ANIME_BUNDLE_KEY, lastAnime);
            EventBus.getDefault().post(new LastAnimeEvent(lastAnime));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
        animeProvider = null;
        unsubscribe();
    }

    private void unsubscribe () {
        if (animeSubscription != null && !animeSubscription.isUnsubscribed()) {
            animeSubscription.unsubscribe();
        }
        if (episodeSubscription != null && !episodeSubscription.isUnsubscribed()) {
            episodeSubscription.unsubscribe();
        }
        if (videoSubscription != null && !videoSubscription.isUnsubscribed()) {
            videoSubscription.unsubscribe();
        }
    }

    private Anime setAnimeProvider (Anime anime) {
        if (anime.getProviderType() != null) {
            switch (anime.getProviderType()) {
                case Providers.RUSH:
                    animeProvider = new RushAnimeProvider();
                    break;
                case Providers.RAM:
                    animeProvider = new RamAnimeProvider();
                    break;
                case Providers.BAM:
                    animeProvider = new BamAnimeProvider();
                    break;
                case Providers.KISS:
                    animeProvider = new KissAnimeProvider();
                    break;
                default:
                    try {
                        anime.setProviderType(GeneralUtils.determineProviderType(anime.getUrl()));
                        setAnimeProvider(anime);
                    } catch (Exception e) {
                        postError(e);
                    }
                    break;
            }
        } else {
            try {
                anime.setProviderType(GeneralUtils.determineProviderType(anime.getUrl()));
                setAnimeProvider(anime);
            } catch (Exception e) {
                postError(e);
            }
        }
        return anime;
    }

    public void onEvent (OpenAnimeEvent event) {
        if (lastAnime == null || !event.anime.getProviderType().equals(lastAnime.getProviderType())) {
            lastAnime = setAnimeProvider(event.anime);
        } else {
            lastAnime = event.anime;
        }

        if (lastAnime != null && lastAnime.getEpisodes() != null) {
            if (getView() != null) {
                getView().setAnime(lastAnime);
            }
            fetchAnime(true);
        } else {
            fetchAnime(false);
        }
    }

    public void fetchAnime (boolean updateCached) {
        isRefreshing = true;
        if (getView() != null) {
            getView().updateRefreshing();
        }

        if (animeSubscription != null && !animeSubscription.isUnsubscribed()) {
            animeSubscription.unsubscribe();
        }

        if (animeProvider == null) setAnimeProvider(lastAnime);

        animeSubscription = Observable.defer(new Func0<Observable<Anime>>() {
            @Override
            public Observable<Anime> call() {
                if (updateCached) {
                    try {
                        return Observable.just(animeProvider.updateCachedAnime(lastAnime));
                    } catch (CloudFlareInitializationException cf) {
                            //CloudflareHttpClient.INSTANCE.registerSites();
                            return Observable.error(new Throwable("Wait 5 seconds and try again (or don't)."));
                    }
                }
                try {
                    return Observable.just(animeProvider.fetchAnime(lastAnime.getUrl()));
                } catch (CloudFlareInitializationException cf) {
                    //CloudflareHttpClient.INSTANCE.registerSites();
                    return Observable.error(new Throwable("Wait 5 seconds and try again (or don't)."));
                }
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(this.deliverLatestCache())
                .subscribe(new Subscriber<Anime>() {
                    @Override
                    public void onNext(Anime anime) {
                        lastAnime = anime;
                        isRefreshing = false;
                        if (getView() != null) {
                            getView().setAnime(lastAnime);
                        }
                        //EventBus.getDefault().post(new LastAnimeEvent(lastAnime)); would save it without a major colour
                        this.unsubscribe();
                    }

                    @Override
                    public void onCompleted() {
                        // should be using Observable.just() as onCompleted is never called
                        // and it only runs once.
                    }

                    @Override
                    public void onError(Throwable e) {
                        isRefreshing = false;
                        getView().updateRefreshing();
                        postError(e);
                        this.unsubscribe();
                    }

                });
    }

    public Anime getCurrentAnime() {
        return lastAnime;
    }

    public void setNeedToGiveFavourite (boolean bool) {
        needToGiveFavouriteState = bool;
    }

    public void setMajorColour (Palette palette) {
        if (palette != null && lastAnime != null) {
            if (palette.getVibrantSwatch() != null) {
                lastAnime.setMajorColour(palette.getVibrantSwatch().getRgb());
            } else if (palette.getLightVibrantSwatch() != null) {
                lastAnime.setMajorColour(palette.getLightVibrantSwatch().getRgb());
            } else if (palette.getDarkMutedSwatch() != null) {
                lastAnime.setMajorColour(palette.getDarkMutedSwatch().getRgb());
            }
        }
    }

    public void onFavouriteCheckedChanged (boolean b) {
        EventBus.getDefault().post(new FavouriteEvent(b, lastAnime));
    }

    public void download (String url, String fileName) {
        if (MainModel.externalDownload) {
            if (getView() != null) {
                GeneralUtils.lazyDownload((AppCompatActivity) getView().getActivity(), url);
            }
        } else {
            SharedPreferences preferences = getView().getActivity().getPreferences(Context.MODE_PRIVATE);
            DownloadManager downloadManager = (DownloadManager) getView().getActivity().getSystemService(Context.DOWNLOAD_SERVICE);

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle(fileName);
            request.setDestinationInExternalPublicDir(preferences.getString(SettingsFragment.DOWNLOAD_LOCATION_PREFERENCE, Environment.DIRECTORY_DOWNLOADS), fileName);
            request.setMimeType("video/*");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

            downloadManager.enqueue(request);
        }
    }
    public void downloadEpisode(String url, int episodePosition) {
        download(url, lastAnime.getEpisodes().get(episodePosition).getTitle() + ".mp4");
    }

    public void postIntent (String videoUrl) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.parse(videoUrl), "video/*");
        if (intent.resolveActivity(getView().getActivity().getPackageManager()) != null) {
            getView().startActivity(intent);
        }
    }

    public void flipWatched (int position) {
        lastAnime.getEpisodes().get(position).flipWatched();
        getView().notifyAdapter();
    }

    public void fetchSources (String url) {
        if (episodeSubscription != null) {
            if (!episodeSubscription.isUnsubscribed()) {
                episodeSubscription.unsubscribe();
            }
        }
        
        episodeSubscription = Observable.defer(new Func0<Observable<List<Source>>>() {
            @Override
            public Observable<List<Source>> call() {
                try {
                    return Observable.just(animeProvider.fetchSources(url));
                } catch (CloudFlareInitializationException cf) {
                    //CloudflareHttpClient.INSTANCE.registerSites();
                    return Observable.error(new Throwable("Wait 5 seconds and try again (or don't)."));
                }
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(this.deliver())
                .subscribe(new Subscriber<List<Source>>() {
                    @Override
                    public void onNext(List<Source> sources) {
                        getView().showSourcesDialog(sources);
                        episodeSubscription.unsubscribe();
                    }

                    @Override
                    public void onCompleted() {
                        // should be using Observable.just() as onCompleted is never called
                        // and it only runs once.
                    }

                    @Override
                    public void onError(Throwable e) {
                        postError(e);
                        this.unsubscribe();
                    }

                });
    }

    public void fetchVideo (Source source, boolean download) {
        if (videoSubscription != null) {
            if (!videoSubscription.isUnsubscribed()) {
                videoSubscription.unsubscribe();
            }
        }

        videoSubscription = Observable.defer(new Func0<Observable<Source>>() {
            @Override
            public Observable<Source> call() {
                try {
                    return Observable.just(animeProvider.fetchVideo(source));
                } catch (CloudFlareInitializationException cf) {
                    //CloudflareHttpClient.INSTANCE.registerSites();
                    return Observable.error(new Throwable("Wait 5 seconds and try again (or don't)."));
                }
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(this.deliver())
                // this subscriber stays here because it needs the 'lazyDownload'
                .subscribe(new Subscriber<Source>() {
                    @Override
                    public void onNext(Source source) {
                        getView().shareVideo(source, download);
                        this.unsubscribe();
                    }

                    @Override
                    public void onCompleted() {
                        // should be using Observable.just() as onCompleted is never called
                        // and it only runs once.
                    }

                    @Override
                    public void onError(Throwable e) {
                        postError(e);
                        this.unsubscribe();
                    }

                });
    }

    public void postError (Throwable e) {
        e.printStackTrace();

        if (getView() != null) {
            View.OnClickListener actionOnClick = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    fetchAnime(true);
                }
            };

            EventBus.getDefault().post(new SnackbarEvent(
                    GeneralUtils.formatError(e),
                    Snackbar.LENGTH_LONG,
                    "Retry",
                    actionOnClick,
                    getView().getResources().getColor(R.color.accent)
            ));
        } else {
            EventBus.getDefault().post(new SnackbarEvent(GeneralUtils.formatError(e)));
        }

    }

    @Override
    public Anime getData() {
        return lastAnime;
    }
}
