package fr.xebia.microsoftoxforddemo.ui;

import android.Manifest;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.util.Base64;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import butterknife.Bind;
import butterknife.OnClick;
import fr.xebia.microsoftoxforddemo.BuildConfig;
import fr.xebia.microsoftoxforddemo.R;
import fr.xebia.microsoftoxforddemo.api.RestService;
import fr.xebia.microsoftoxforddemo.model.MatchRequest;
import fr.xebia.microsoftoxforddemo.util.ImageUtil;
import okhttp3.OkHttpClient;
import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.OnNeverAskAgain;
import permissions.dispatcher.OnPermissionDenied;
import permissions.dispatcher.RuntimePermissions;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

@RuntimePermissions
public class MainActivity extends BaseActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static final int REQUEST_TAKE_PHOTO = 0;
    private static final int REQUEST_SELECT_IMAGE_IN_ALBUM = 1;

    @Bind(R.id.chosen_image) ImageView chosenImage;
    @Bind(R.id.match_progressbar) ProgressBar matchProgressBar;
    @Bind(R.id.matching_progress_text) TextView matchProgressText;
    @Bind(R.id.match_progress) ViewGroup matchProgress;

    private RestService restService;

    private ActionBarDrawerToggle toggle;
    private DrawerLayout drawer;
    private NavigationView navigationView;

    private Uri uriPhotoTaken;
    private Bitmap bitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        OkHttpClient client = new OkHttpClient.Builder()
                .readTimeout(20_000, TimeUnit.MILLISECONDS)
                .writeTimeout(30_000, TimeUnit.MILLISECONDS)
                .build();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BuildConfig.API_EP)
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build();

        restService = retrofit.create(RestService.class);

        setContentView(R.layout.activity_main);

        actionBar.setTitle(R.string.title_activity_main);

        drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        navigationView.getMenu().getItem(0).setChecked(true);
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        drawer.removeDrawerListener(toggle);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_about) {
            startActivity(new Intent(this, AboutActivity.class));
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_TAKE_PHOTO || requestCode == REQUEST_SELECT_IMAGE_IN_ALBUM) {
            if (resultCode == RESULT_OK) {
                Uri imageUri;
                if (data == null || data.getData() == null) {
                    imageUri = uriPhotoTaken;
                } else {
                    imageUri = data.getData();
                }
                displayImage(imageUri);
            }
        }
    }

    @OnClick(R.id.btn_take_photo)
    public void onClickButtonTakePhoto(View v) {
        MainActivityPermissionsDispatcher.takePhotoWithCheck(this);
    }

    @OnClick(R.id.btn_select_from_gallery)
    public void onClickButtonSelectFromGallery(View v) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, REQUEST_SELECT_IMAGE_IN_ALBUM);
        }
    }

    @OnClick(R.id.btn_match)
    public void onClickButtonMatch(View v) {
        if (bitmap != null) {
            displayProgressIndicators();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos); //bm is the bitmap object
            String encodedImage = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
            MatchRequest request = new MatchRequest(encodedImage);
            restService.match(request)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Subscriber<String>() {
                        @Override
                        public void onCompleted() {

                        }

                        @Override
                        public void onError(Throwable e) {
                            onMatchFailed();
                        }

                        @Override
                        public void onNext(String result) {
                            onMatchSuccess(result);
                        }
                    });
        } else {
            hideProgressIndicator();
            Toast.makeText(this, R.string.no_bitmap_available, Toast.LENGTH_LONG).show();
        }
    }

    // UI Helper

    private void displayProgressIndicators() {
        matchProgress.setVisibility(View.VISIBLE);
        matchProgressBar.setVisibility(View.VISIBLE);
        matchProgressText.setText(getString(R.string.matching));
    }

    private void hideProgressIndicator() {
        matchProgress.setVisibility(View.GONE);
        matchProgress.animate().translationY(0).start();
    }

    private void onMatchFailed() {
        matchProgressBar.setVisibility(View.GONE);
        matchProgressText.setText(getString(R.string.no_match));
    }

    private void onMatchSuccess(String result) {
        matchProgressBar.setVisibility(View.GONE);
        matchProgressText.setText(String.format(getString(R.string.match_found), result));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        MainActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);
    }

    @NeedsPermission({Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE})
    void takePhoto() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            File storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            try {
                // Save the photo taken to a temporary file
                File file = File.createTempFile("IMG_", ".jpg", storageDir);
                uriPhotoTaken = Uri.fromFile(file);
                addPhotoToGallery(uriPhotoTaken.getPath());
                intent.putExtra(MediaStore.EXTRA_OUTPUT, uriPhotoTaken);
                startActivityForResult(intent, REQUEST_TAKE_PHOTO);
            } catch (IOException e) {
                Toast.makeText(this, R.string.error_take_photo, Toast.LENGTH_LONG).show();
            }
        }
    }

    @OnPermissionDenied({Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE})
    void showDeniedForTakePhoto() {
        Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
    }

    @OnNeverAskAgain({Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE})
    void showNeverAskForTakePhoto() {
        Toast.makeText(this, R.string.permission_neverask, Toast.LENGTH_SHORT).show();
    }

    private void displayImage(Uri imageUri) {
        hideProgressIndicator();
        bitmap = ImageUtil.loadSizeLimitedBitmapFromUri(imageUri, getContentResolver());
        if (bitmap != null) {
            chosenImage.setImageBitmap(bitmap);
        }
    }

    private void addPhotoToGallery(String currentPhotoPath) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        File f = new File(currentPhotoPath);
        Uri contentUri = Uri.fromFile(f);
        mediaScanIntent.setData(contentUri);
        sendBroadcast(mediaScanIntent);
    }
}
