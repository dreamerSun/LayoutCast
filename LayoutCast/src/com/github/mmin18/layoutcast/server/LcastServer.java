package com.github.mmin18.layoutcast.server;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;

import android.app.Application;
import android.content.Context;
import android.content.res.Resources;
import android.util.Log;

import com.github.mmin18.layoutcast.context.OverrideContext;
import com.github.mmin18.layoutcast.util.EmbedHttpServer;
import com.github.mmin18.layoutcast.util.ResUtils;

/**
 * GET /packagename (get the application package name)<br>
 * POST /pushres (upload resources file)<br>
 * PUT /pushres (upload resources file)<br>
 * POST /lcast (cast to all activities)<br>
 * POST /reset (reset all activities)<br>
 * GET /ids.xml<br>
 * GET /public.xml<br>
 * 
 * @author mmin18
 */
public class LcastServer extends EmbedHttpServer {
	public static final int PORT_FROM = 41128;
	public static Application app;
	final Context context;
	File latestPushFile;

	private LcastServer(Context ctx, int port) {
		super(port);
		context = ctx;
	}

	@Override
	protected void handle(String method, String path,
			HashMap<String, String> headers, InputStream input,
			ResponseOutputStream response) throws Exception {
		if (path.equalsIgnoreCase("/packagename")) {
			response.setContentTypeText();
			response.write(context.getPackageName().getBytes("utf-8"));
			return;
		}
		if (("post".equalsIgnoreCase(method) || "put".equalsIgnoreCase(method))
				&& path.equalsIgnoreCase("/pushres")) {
			File dir = new File(context.getCacheDir(), "lcast");
			dir.mkdir();
			File file = new File(dir, Integer.toHexString((int) (System
					.currentTimeMillis() / 100) & 0xfff) + ".apk");
			FileOutputStream fos = new FileOutputStream(file);
			byte[] buf = new byte[4096];
			int l;
			while ((l = input.read(buf)) != -1) {
				fos.write(buf, 0, l);
			}
			fos.close();
			latestPushFile = file;
			response.setStatusCode(201);
			Log.d("lcast", "lcast resources file received (" + file.length()
					+ " bytes): " + file);
			return;
		}
		if ("/lcast".equalsIgnoreCase(path)) {
			Resources res = ResUtils.getResources(app, latestPushFile);
			OverrideContext.setGlobalResources(res);
			response.setStatusCode(200);
			response.write(String.valueOf(latestPushFile).getBytes());
			return;
		}
		if ("/reset".equalsIgnoreCase(path)) {
			OverrideContext.setGlobalResources(null);
			response.setStatusCode(200);
			response.write("OK".getBytes());
			return;
		}
		if ("/ids.xml".equalsIgnoreCase(path)) {
			String Rn = app.getPackageName() + ".R";
			Class<?> Rclazz = app.getClassLoader().loadClass(Rn);
			String str = new IdProfileBuilder(context.getResources())
					.buildIds(Rclazz);
			response.setStatusCode(200);
			response.setContentTypeText();
			response.write(str.getBytes());
			return;
		}
		if ("/public.xml".equalsIgnoreCase(path)) {
			String Rn = app.getPackageName() + ".R";
			Class<?> Rclazz = app.getClassLoader().loadClass(Rn);
			String str = new IdProfileBuilder(context.getResources())
					.buildPublic(Rclazz);
			response.setStatusCode(200);
			response.setContentTypeText();
			response.write(str.getBytes());
			return;
		}
		super.handle(method, path, headers, input, response);
	}

	private static LcastServer runningServer;

	public static void start(Context ctx) {
		if (runningServer != null) {
			Log.d("lcast", "lcast server is already running");
			return;
		}

		for (int i = 0; i < 100; i++) {
			LcastServer s = new LcastServer(ctx, PORT_FROM + i);
			try {
				s.start();
				runningServer = s;
				Log.d("lcast", "lcast server running on port "
						+ (PORT_FROM + i));
				break;
			} catch (Exception e) {
			}
		}
	}

	public static void cleanCache(Context ctx) {
		File dir = new File(ctx.getCacheDir(), "lcast");
		File[] fs = dir.listFiles();
		if (fs != null) {
			for (File f : fs) {
				rm(f);
			}
		}
	}

	private static void rm(File f) {
		if (f.isDirectory()) {
			for (File ff : f.listFiles()) {
				rm(ff);
			}
		} else {
			f.delete();
		}
	}
}
