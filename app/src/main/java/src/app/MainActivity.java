package src.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.io.FileOutputStream;

import android.database.Cursor;
import android.content.ContentValues;
import androidx.core.content.FileProvider;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_INSTALL = 1001;
    private static final int REQUEST_FILE_PICK = 1002;
    private static final int REQUEST_STORAGE = 1003;
    private Uri lastSelectedUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 处理通过其他应用打开APK的情况
        handleIntent(getIntent());

        // 在onCreate中添加权限请求
        requestStoragePermission();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri apkUri = intent.getData();
            if (apkUri != null) {
                installApk(apkUri);
            }
        }
    }

    private void installApk(Uri apkUri) {
        try {
            // 验证文件有效性
            if (!isValidApk(apkUri)) {
                Toast.makeText(this, "无效的APK文件", Toast.LENGTH_SHORT).show();
                return;
            }

            // 智能重命名逻辑
            Uri finalUri = ensureApkExtension(apkUri);

            Intent installIntent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            installIntent.setData(finalUri);
            installIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(installIntent);
        } catch (SecurityException e) {
            Toast.makeText(this, "文件访问权限已过期，请重新选择", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "文件处理失败", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isValidApk(Uri apkUri) {
        try (InputStream is = getContentResolver().openInputStream(apkUri)) {
            // 仅通过ZIP头验证，不检查文件名
            byte[] header = new byte[4];
            return is.read(header) == 4 &&
                    header[0] == 0x50 &&
                    header[1] == 0x4B &&
                    header[2] == 0x03 &&
                    header[3] == 0x04;
        } catch (IOException e) {
            return false;
        }
    }

    // 新增智能重命名方法
    private Uri ensureApkExtension(Uri originalUri) throws IOException {
        String fileName = getFileName(originalUri);

        // 如果已有.apk扩展名则直接返回
        if (fileName != null && fileName.toLowerCase().endsWith(".apk")) {
            return originalUri;
        }

        // 创建临时文件并复制内容
        File cacheDir = new File(getCacheDir(), "apk_cache");
        if (!cacheDir.exists())
            cacheDir.mkdirs();
        File tempFile = File.createTempFile("temp_", ".apk", cacheDir);

        try (InputStream is = getContentResolver().openInputStream(originalUri);
                FileOutputStream fos = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = is.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
        }

        return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", tempFile);
    }

    // 恢复获取文件名的方法（安全版本）
    private String getFileName(Uri uri) {
        String name = null;

        // 优先尝试通过ContentResolver获取
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (columnIndex != -1) {
                        name = cursor.getString(columnIndex);
                    }
                }
            } catch (Exception e) {
                // 忽略查询异常，回退到URI解析
            }
        }

        // 回退到URI路径解析
        if (name == null) {
            String path = uri.getPath();
            if (path != null) {
                int cut = path.lastIndexOf('/');
                name = cut != -1 ? path.substring(cut + 1) : path;
            }
        }

        return name != null ? name : "unknown_file";
    }

    // 处理低版本设备的未知来源提示
    private void showUnknownSourceAlert() {
        new AlertDialog.Builder(this)
                .setTitle("安装权限提示")
                .setMessage("请前往设置开启「未知来源」安装权限")
                .setPositiveButton("去设置", (d, w) -> {
                    Intent intent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
                    startActivity(intent);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // 添加按钮点击处理方法
    public void onInstallClick(View view) {
        // 先请求权限再选择文件
        pickApkFile();
    }

    // 添加缺失的文件选择方法
    private void pickApkFile() {
        // 使用官方推荐的文件选择器
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/vnd.android.package-archive");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                "application/vnd.android.package-archive",
                "application/octet-stream" // 兼容微信修改的后缀
        });
        startActivityForResult(intent, REQUEST_FILE_PICK);
    }

    // 修改onActivityResult中的处理逻辑
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == REQUEST_FILE_PICK) {
            Uri uri = data.getData();
            if (uri != null) {
                // 直接使用原始URI，不再重命名
                installApk(uri);
            }
        } else if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_INSTALL) {
                // 重新尝试安装
                installApk(lastSelectedUri);
            }
        }
    }

    // 在onCreate中添加权限请求
    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                        new String[] { Manifest.permission.READ_EXTERNAL_STORAGE },
                        REQUEST_STORAGE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限通过后重新尝试安装
                installApk(lastSelectedUri);
            }
        }
    }

    private void checkAndRequestPermissions(Uri uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

                // 显示解释对话框
                new AlertDialog.Builder(this)
                        .setTitle("需要存储权限")
                        .setMessage("此权限仅用于读取您选择的APK文件，我们不会访问其他文件")
                        .setPositiveButton("同意", (d, w) -> {
                            requestPermissions(
                                    new String[] { Manifest.permission.READ_EXTERNAL_STORAGE },
                                    REQUEST_STORAGE);
                        })
                        .setNegativeButton("拒绝", (d, w) -> {
                            Toast.makeText(this, "功能将受限", Toast.LENGTH_SHORT).show();
                        })
                        .show();
            } else {
                installApk(uri);
            }
        } else {
            installApk(uri);
        }
    }

    // 在关于对话框中显示
    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("隐私声明")
                .setMessage(R.string.permission_explanation)
                .setPositiveButton("查看源码", (d, w) -> {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/MinerTob/wechatapk"));
                    startActivity(browserIntent);
                })
                .setNegativeButton("关闭", null)
                .show();
    }
}
