package com.mini.market.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.NotificationCompat.Builder;
import android.text.TextUtils;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.mini.market.R;
import com.mini.market.http.HASH;
import com.mini.market.service.DownloadService;

/**
 * 广告下载类
 * 
 * @author 庄宏岩
 * 
 */
public class AppDownload {
	public Context mContext;
	private int NF_ID = 1003;
	private Notification nf;
	private NotificationManager nm;
	private String downloadurl;
	private String appName;
	private String packageName;
	private Bitmap appIcon;
	private Builder builder;
	private DownloadListener downloadListener;

	public AppDownload(Context context) {
		this.mContext = context;
	}

	private ExecutorService mAppThreadPool = null;

	/**
	 * 获取线程池的方法，因为涉及到并发的问题，我们加上同步锁
	 * 
	 * @return
	 */
	public ExecutorService getThreadPool() {
		if (mAppThreadPool == null) {
			synchronized (ExecutorService.class) {
				if (mAppThreadPool == null) {
					mAppThreadPool = Executors.newFixedThreadPool(2);
				}
			}
		}

		return mAppThreadPool;

	}

	public void createNotify() {
		String time = System.currentTimeMillis() + "";
		NF_ID = Integer.parseInt(time.substring(time.length() / 2, time.length()));
		nm = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			builder = new Builder(mContext);
			builder.setSmallIcon(R.drawable.ic_launcher);
			if (appIcon == null) {
				appIcon = ((BitmapDrawable) mContext.getResources().getDrawable(R.drawable.ic_launcher)).getBitmap();
			}
			builder.setLargeIcon(appIcon);
			builder.setContentTitle(appName);
			builder.setContentText(mContext.getString(R.string.app_download_progress));
			builder.setProgress(100, 0, false);
			builder.setOngoing(true);

			nf = builder.build();
			nf.flags = Notification.FLAG_NO_CLEAR;
			nf.flags = Notification.FLAG_ONGOING_EVENT;  
			nf.flags |= Notification.FLAG_FOREGROUND_SERVICE;  
			nf.icon = android.R.drawable.stat_sys_download;
			nf.contentIntent = PendingIntent.getActivity(mContext, 0, new Intent(), 0);
			nf = builder.build();
		} else {
			nf = new Notification(R.drawable.ic_launcher, "", System.currentTimeMillis());
			nf.icon = android.R.drawable.stat_sys_download;
			nf.flags = Notification.FLAG_NO_CLEAR;
			nf.flags = Notification.FLAG_ONGOING_EVENT;  
			nf.flags |= Notification.FLAG_FOREGROUND_SERVICE;  
			nf.contentView = new RemoteViews(mContext.getPackageName(), R.layout.notification_layout);
			nf.contentView.setProgressBar(R.id.progressbar_notification, 100, 0, false);
			nf.contentView.setTextViewText(R.id.textivew_notification, mContext.getString(R.string.app_download_progress));
			nf.contentIntent = PendingIntent.getActivity(mContext, 0, new Intent(), 0);
		}
	}

	public void setDownloadurl(String downloadurl) {
		this.downloadurl = downloadurl;
	}

	public void setAppBitmapIcon(Bitmap appIcon) {
		if (appIcon != null) {
			int newWidth = DensityUtil.dip2px(mContext, 48);
			if (appIcon.getWidth() > newWidth) {
				Bitmap bitmap = DrawableUtils.scaleTo(appIcon, newWidth, newWidth);
				this.appIcon = bitmap;
			} else {
				this.appIcon = appIcon;
			}
		}
	}

	public void setAppName(String appName) {
		this.appName = appName;
	}

	public void setPackageName(String packageName) {
		this.packageName = packageName;
	}

	private Handler mHandler = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			switch (msg.what) {
			case 3: {
				downloadListener.onError(downloadurl);
				if (DownloadService.downloadingList != null && DownloadService.downloadingList.size() > 0) {
					DownloadService.downloadingList.remove(packageName);
				}
				nm.cancel(NF_ID);
				Toast.makeText(mContext, R.string.appupdate_result_download_error, Toast.LENGTH_SHORT).show();
				break;
			}
			case 4: {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
					builder.setContentText(msg.obj + "%");
					builder.setProgress(100, (Integer) msg.obj, false);
					nf = builder.build();
				} else {
					nf.contentView.setProgressBar(R.id.progressbar_notification, 100, (Integer) msg.obj, false);
					nf.contentView.setTextViewText(R.id.textivew_notification, appName + mContext.getString(R.string.app_download_progress) + " " + msg.obj
							+ "%");
				}
				nm.notify(NF_ID, nf);
				break;
			}
			case 5: {
				downloadListener.onDownloaded();
				Intent intent = new Intent();
				intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				intent.setAction(android.content.Intent.ACTION_VIEW);
				File file = new File((String) msg.obj);
				if (file.exists() && file.isAbsolute()) {
					intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive");
					mContext.startActivity(intent);
					if (!TextUtils.isEmpty(appName)) {
						CustomEventCommit.commit(mContext, CustomEventCommit.ad_downloaded, appName);
					}
				}
				nm.cancel(NF_ID);
				break;
			}
			case 6: {
				downloadListener.onInstalled();
				nm.cancel(NF_ID);

				if (packageName != null) {
					mContext.startActivity(mContext.getPackageManager().getLaunchIntentForPackage(packageName));
					if (!TextUtils.isEmpty(appName)) {
						CustomEventCommit.commit(mContext, CustomEventCommit.app_open, appName);
					}
				}
				break;
			}
			default:
				break;
			}
		}
	};

	/**
	 * 开始下载app
	 */
	public void startDownloadApp(DownloadListener downloadListener) {
		this.downloadListener = downloadListener;
		CustomEventCommit.commit(mContext, CustomEventCommit.ad_download, appName);
		Message msg2 = new Message();
		msg2.obj = 0;
		msg2.what = 4;
		mHandler.sendMessage(msg2);
		getThreadPool().execute(new Runnable() {

			@Override
			public void run() {
				String path = downloadurl;
				String apkName = HASH.md5sum(path);
				String downloadPath = Constants.DOWNLOAD_PATH;
				if (!new File(downloadPath).exists()) {
					new File(downloadPath).mkdirs();
				}
				String apkPath = downloadPath + "/" + apkName;
				String apkPathTemp = downloadPath + "/" + apkName + ".temp";
				if (new File(apkPath).exists()) {
					if (Terminal.isRoot(mContext)) {
						if (Terminal.installApp(apkPath)) {
							mHandler.sendEmptyMessage(6);
						} else {
							Message msg = new Message();
							msg.what = 5;
							msg.obj = apkPath;
							mHandler.sendMessage(msg);
						}
					} else {
						Message msg = new Message();
						msg.what = 5;
						msg.obj = apkPath;
						mHandler.sendMessage(msg);
					}
					return;
				}
				long totalSize = 0;
				long progress = 0;
				long bytes = new File(apkPathTemp).exists() ? new File(apkPathTemp).length() : 0;
				long downloadSize = bytes;
				try {
					URL url = new URL(path);
					HttpURLConnection conn = (HttpURLConnection) url.openConnection();
					conn.setConnectTimeout(5 * 1000);
					conn.setRequestMethod("GET");
					conn.setDoInput(true);
					conn.setInstanceFollowRedirects(true);
					conn.setRequestProperty("User-Agent", "Ray-Downer");
					conn.setRequestProperty("RANGE", "bytes=" + bytes + "-");
					conn.setReadTimeout(30 * 1000);
					int connCode = conn.getResponseCode();
					if (connCode < 400 && connCode >= 200) {
						InputStream inStream = conn.getInputStream();
						FileOutputStream outputStream = new FileOutputStream(apkPathTemp, true);
						byte[] buffer = new byte[4096];
						totalSize = conn.getContentLength();
						totalSize += bytes;
						int len;
						while ((len = inStream.read(buffer)) != -1) {
							outputStream.write(buffer, 0, len);
							downloadSize += len;
							int newProgress = (int) ((100 * downloadSize) / totalSize);
							if (newProgress - 1 > progress) {
								progress = newProgress;
								Message msg = new Message();
								msg.obj = newProgress;
								msg.what = 4;
								mHandler.sendMessage(msg);
							}
						}
						inStream.close();
						outputStream.close();
						if (totalSize == downloadSize) {
							new File(apkPathTemp).renameTo(new File(apkPath));
							if (Terminal.isRoot(mContext)) {
								if (Terminal.installApp(apkPath)) {
									mHandler.sendEmptyMessage(6);
								} else {
									Message msg = new Message();
									msg.what = 5;
									msg.obj = apkPath;
									mHandler.sendMessage(msg);
								}
							} else {
								Message msg = new Message();
								msg.what = 5;
								msg.obj = apkPath;
								mHandler.sendMessage(msg);
							}
						}
					} else {
						DLog.i("debug", "url: " + downloadurl + " connCode : " + connCode);
						mHandler.sendEmptyMessage(3);
					}
				} catch (Exception e) {
					mHandler.sendEmptyMessage(3);
					e.printStackTrace();
				}

			}
		});

	}

	/**
	 * 取消正在下载的任务
	 */
	public synchronized void cancelTask() {
		if (mAppThreadPool != null) {
			mAppThreadPool.shutdownNow();
			mAppThreadPool = null;
		}
	}

	public interface DownloadListener {
		void onError(String url);

		void onInstalled();

		void onDownloaded();
	}

}
