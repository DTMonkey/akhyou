package dulleh.akhyou;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import org.jsoup.Jsoup;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.greenrobot.event.EventBus;
import dulleh.akhyou.Models.Anime;
import dulleh.akhyou.Models.Hummingbird.HummingbirdApi;
import dulleh.akhyou.Utils.C;
import dulleh.akhyou.Utils.Events.FavouriteEvent;
import dulleh.akhyou.Utils.Events.HbUserEvent;
import dulleh.akhyou.Utils.Events.HummingbirdCredentialsUpdatedEvent;
import dulleh.akhyou.Utils.Events.HummingbirdSettingsEvent;
import dulleh.akhyou.Utils.Events.LastAnimeEvent;
import dulleh.akhyou.Utils.Events.OpenAnimeEvent;
import dulleh.akhyou.Utils.Events.SearchEvent;
import dulleh.akhyou.Utils.Events.SearchSubmittedEvent;
import dulleh.akhyou.Utils.Events.SnackbarEvent;
import dulleh.akhyou.Utils.GeneralUtils;
import nucleus.presenter.RxPresenter;
import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func0;
import rx.schedulers.Schedulers;

public class MainPresenter extends RxPresenter<MainActivity>{
    private static final String FAVOURITES_KEY = "favourites_key";

    private MainModel mainModel;

    private boolean needFragment = false;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        EventBus.getDefault().register(this);
        if (savedState != null && mainModel != null ) {
            ArrayList<Anime> favourites = savedState.getParcelableArrayList(FAVOURITES_KEY);
            if (favourites != null) {
                mainModel.setFavourites(favourites);
            }
        }
    }

    @Override
    protected void onTakeView(MainActivity view) {
        super.onTakeView(view);

        if (mainModel != null && mainModel.hasSharedPreferences()) {
            mainModel.refreshHbDisplayNameAndUser();
        }

        if (needFragment) {
            view.requestFragment(MainActivity.SEARCH_FRAGMENT, null);
            needFragment = false;
        }

    }

    @Override
    protected void onSave(Bundle state) {
        super.onSave(state);
        if (mainModel.getFavourites() != null) {
            state.putParcelableArrayList(FAVOURITES_KEY, mainModel.getFavourites());
        }
        mainModel.saveFavourites();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainModel.saveFavourites();
        mainModel = null;
    }

    // must be done every time activity onCreate()
    public void setSharedPreferences (SharedPreferences sharedPreferences) {
        if (mainModel != null) {
            mainModel.setSharedPreferences(sharedPreferences);
        } else {
            mainModel = new MainModel(sharedPreferences);
        }
    }

    public MainModel getModel () {
        return mainModel;
    }

    public void launchFromHbLink (String url) {
        Observable.defer(new Func0<Observable<String>>() {
            @Override
            public Observable<String> call() {
                return Observable.just(HummingbirdApi.getTitleFromRegularPage(url));
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(this.deliver())
                .subscribe(new Subscriber<String>() {
                    @Override
                    public void onNext(String title) {
                        EventBus.getDefault().postSticky(new SearchEvent(title));
                    }

                    @Override
                    public void onCompleted() {
                        if (getView() != null) {
                            getView().requestFragment(MainActivity.SEARCH_FRAGMENT, null);
                        } else {
                            needFragment = true;
                        }
                        this.unsubscribe();
                    }

                    @Override
                    public void onError(Throwable e) {
                        e.printStackTrace();
                        getView().showSnackBar(new SnackbarEvent(GeneralUtils.formatError(e)));
                        this.unsubscribe();
                    }
                });
    }

    public void launchFromMalLink (String url) {
        Observable.just(url)
                .subscribeOn(Schedulers.io())
                .map(u -> GeneralUtils.getWebPage(u))
                .map(body -> Jsoup.parse(body).select("head > title").first())
                .map(element -> element.text().substring(0, element.text().lastIndexOf("-") - 1))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<String>() {
                    @Override
                    public void onNext(String title) {
                        EventBus.getDefault().postSticky(new SearchEvent(title));
                    }

                    @Override
                    public void onCompleted() {
                        if (getView() != null) {
                            getView().requestFragment(MainActivity.SEARCH_FRAGMENT, null);
                        } else {
                            needFragment = true;
                        }
                        this.unsubscribe();
                    }

                    @Override
                    public void onError(Throwable e) {
                        e.printStackTrace();
                        getView().showSnackBar(new SnackbarEvent(GeneralUtils.formatError(e)));
                        this.unsubscribe();
                    }
                });
    }

    public void refreshFavouritesList () {
        mainModel.refreshFavourites();
    }

    public String getUserAvatar () {
        return mainModel.getHbUser().getAvatar();
    }

    public List<Anime> getFavourites () {
        return mainModel.getFavourites();
    }

    public void onStart(Bundle savedInstanceState, Intent openingIntent, OnlyFragmentManager onlyFragmentManager) {
        String action = openingIntent.getAction();

        if (action == null) {
            if (savedInstanceState != null) {
                return;
            }
        } else if (action.equals(Intent.ACTION_MAIN)) {
            if (savedInstanceState != null) {
                return;
            }
        } else if (action.equals(Intent.ACTION_VIEW)) {
            Uri uri = openingIntent.getData();

            if (uri == null || uri.getPath().isEmpty() || uri.getHost() == null || uri.getHost().isEmpty()) {
                if (savedInstanceState != null) {
                    return;
                }
            } else {
                String host = uri.getHost();
                String path = uri.getPath();
                if ((host.equalsIgnoreCase(C.HOST_HUMMINGBIRD) || host.equalsIgnoreCase(C.HOST_WWW_HUMMINGBIRD))
                        && (path.toLowerCase().contains("/anime/") || path.toLowerCase().contains("/a/"))) {
                    launchFromHbLink(uri.toString());
                    return;
                } else if ((host.equalsIgnoreCase(C.HOST_MYANIMELIST) || host.equalsIgnoreCase(C.HOST_WWW_MYANIMELIST))
                        && uri.getPath().toLowerCase().contains("/anime/")) {
                    launchFromMalLink(uri.toString());
                    return;
                } else if (savedInstanceState != null) {
                    return;
                }
            }
        } else if (action.equals(Intent.ACTION_SEND)) {
            String extra = openingIntent.getStringExtra(Intent.EXTRA_TEXT);
            if (extra != null && !extra.isEmpty()) {
                Pattern pattern = Pattern.compile("\\S*(" + C.HOST_HUMMINGBIRD + "|" + C.HOST_MYANIMELIST + ")\\S*");
                Matcher matcher = pattern.matcher(extra);
                if (matcher.find()) {
                    String url = matcher.group(0);
                    onStart(savedInstanceState, new Intent(Intent.ACTION_VIEW, Uri.parse(url)), onlyFragmentManager);
                    return;
                } else {
                    onEvent(new SearchSubmittedEvent(extra));
                    return;
                }
            } else {
                onEvent(new SearchSubmittedEvent(extra));
                return;
            }
        }
        onFreshStart(onlyFragmentManager);
    }

    // Must have run setSharedPreferences() before this.
    public void onFreshStart (OnlyFragmentManager onlyFragmentManager) {
        if (mainModel.getLastAnime() != null && MainModel.openToLastAnime) {
            EventBus.getDefault().postSticky(new OpenAnimeEvent(mainModel.getLastAnime()));
            onlyFragmentManager.requestFragment(MainActivity.ANIME_FRAGMENT, null);
        } else {
            onlyFragmentManager.requestFragment(MainActivity.SEARCH_FRAGMENT, null);
        }
        if (!BuildConfig.isFdroidFlav && mainModel.shouldAutoUpdate()) {
            checkForUpdate();
        }
    }

    private void checkForUpdate () {
        Observable.defer(new Func0<Observable<String>>() {
            @Override
            public Observable<String> call() {
                return Observable.just(mainModel.isUpdateAvailable());
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(deliver())
                .subscribe(new Subscriber<String>() {

                    @Override
                    public void onNext(String s) {
                        if (s != null) {
                            getView().promptForUpdate(s);
                        }
                        this.unsubscribe();
                    }

                    @Override
                    public void onCompleted() {
                    }

                    @Override
                    public void onError(Throwable e) {
                        postError(e);
                        this.unsubscribe();
                    }

                });
    }

    public void onEvent (FavouriteEvent event) {
        // colors are inconsistent for whatever reason, causing duplicate favourites,
        // so Set is pretty useless ;-;

        // set mainModel if needed and possible
        if (mainModel == null) {
            if (getView() != null) {
                mainModel = new MainModel(getView().getPreferences(Context.MODE_PRIVATE));
            }
        }

        try {
            if (event.addToFavourites) {
                mainModel.addToFavourites(event.anime);
            } else {
                mainModel.removeFromFavourites(event.anime);
            }
            mainModel.saveFavourites();
            if (getView() != null) {
                getView().favouritesChanged(getFavourites());
            }
        } catch (Exception e) {
            postError(e);
        }

    }

    public void onEvent (LastAnimeEvent event) {
            // THIS METHOD IS BEING EXECUTED
        if (mainModel.updateLastAnimeAndFavourite(event.anime) && getView() != null) {
            mainModel.saveFavourites();
            getView().favouritesChanged(getFavourites());
        }
    }

    public void onEvent (SearchSubmittedEvent event) {
        if (getView() != null) {
            if (getView().getSupportFragmentManager().findFragmentByTag(MainActivity.ANIME_FRAGMENT) != null) {
                getView().getSupportFragmentManager().popBackStack();
            }
            if (getView().getSupportFragmentManager().findFragmentByTag(MainActivity.SEARCH_FRAGMENT) == null) {
                getView().requestFragment(MainActivity.SEARCH_FRAGMENT, null);
            }
        }
        EventBus.getDefault().postSticky(new SearchEvent(event.searchTerm));
    }

    public void onEvent (HummingbirdSettingsEvent event) {
        if (getView() != null) {
            getView().requestFragment(MainActivity.HUMMINGBIRD_SETTINGS_FRAGMENT, null);
        }
    }

    public void onEvent (HummingbirdCredentialsUpdatedEvent event) {
        mainModel.loginHummingbird(event.usernameOrEmail, event.password);
    }

    public void onEvent (HbUserEvent event) {
        if (getView() != null) {
            if (mainModel.getHbUser() != null) {
                getView().refreshDrawerUser(mainModel.getHbDisplayName(),
                        mainModel.getHbUser().getAvatar(),
                        mainModel.getHbUser().getCoverImage());
            } else {
                getView().refreshDrawerUser(mainModel.getHbDisplayName(), null, null);
            }
        }
    }

    public void onEvent (SnackbarEvent event) {
        if (getView() != null) {
            getView().showSnackBar(event);
        }
    }

    public void postError (Throwable e) {
        e.printStackTrace();
        EventBus.getDefault().post(new SnackbarEvent(GeneralUtils.formatError(e)));
    }

    public void downloadUpdate () {
        GeneralUtils.lazyDownload(getView(), mainModel.getUpdateUrl());
    }
}
