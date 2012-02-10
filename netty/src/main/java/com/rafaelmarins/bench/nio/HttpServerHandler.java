package com.rafaelmarins.bench.nio;

import static io.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static io.netty.handler.codec.http.HttpHeaders.setContentLength;
import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpMethod.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import javax.activation.MimetypesFileTypeMap;

import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBuffers;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelFutureProgressListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelStateEvent;
import io.netty.channel.Channels;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.ExceptionEvent;
import io.netty.channel.FileRegion;
import io.netty.channel.MessageEvent;
import io.netty.channel.SimpleChannelUpstreamHandler;
import io.netty.handler.codec.frame.TooLongFrameException;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.CookieDecoder;
import io.netty.handler.codec.http.CookieEncoder;
import io.netty.handler.codec.http.DefaultHttpDataFactory;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DiskAttribute;
import io.netty.handler.codec.http.DiskFileUpload;
import io.netty.handler.codec.http.FileUpload;
import io.netty.handler.codec.http.HttpChunk;
import io.netty.handler.codec.http.HttpDataFactory;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpPostRequestDecoder;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.InterfaceHttpData;
import io.netty.handler.codec.http.HttpPostRequestDecoder.EndOfDataDecoderException;
import io.netty.handler.codec.http.HttpPostRequestDecoder.ErrorDataDecoderException;
import io.netty.handler.codec.http.HttpPostRequestDecoder.IncompatibleDataDecoderException;
import io.netty.handler.codec.http.HttpPostRequestDecoder.NotEnoughDataDecoderException;
import io.netty.handler.codec.http.InterfaceHttpData.HttpDataType;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedFile;
import io.netty.util.CharsetUtil;

@Sharable
public class HttpServerHandler extends SimpleChannelUpstreamHandler {

    public static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
    public static final String HTTP_DATE_GMT_TIMEZONE = "GMT";
    public static final int HTTP_CACHE_SECONDS = 60;

    private Boolean useZeroCopy;
    private String docRoot = System.getProperty("user.dir");
    private String uploadsDir = System.getProperty("user.dir");

    private volatile HttpRequest request;

    private volatile boolean readingChunks;

    private static final HttpDataFactory factory = new DefaultHttpDataFactory(
            DefaultHttpDataFactory.MINSIZE); // Disk if size exceed MINSIZE

    private HttpPostRequestDecoder decoder;
	
    static {
        DiskFileUpload.deleteOnExitTemporaryFile = true; // should delete file
                                                         // on exit (in normal
                                                         // exit)
        DiskFileUpload.baseDirectory = null; // system temp directory
        DiskAttribute.deleteOnExitTemporaryFile = true; // should delete file on
                                                        // exit (in normal exit)
        DiskAttribute.baseDirectory = null; // system temp directory
    }


    public HttpServerHandler() {
    	super();
	}

    public HttpServerHandler(String docRoot) {
		super();
		this.docRoot = docRoot;
	}

    public HttpServerHandler(String docRoot, String uploadsDir) {
		super();
		this.docRoot = docRoot;
		this.uploadsDir = uploadsDir;
	}

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        if (decoder != null) {
            decoder.cleanFiles();
        }
    }

	@Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {

		if (!readingChunks) {
	        HttpRequest request = this.request = (HttpRequest) e.getMessage();
	
	        if (request.getMethod() != GET && request.getMethod() != POST) {
	            sendError(ctx, METHOD_NOT_ALLOWED);
	            return;
	        }
	
	        final String uri = sanitizeUri(request.getUri());
	        if (uri == null) {
	            sendError(ctx, FORBIDDEN);
	            return;
	        }
	
	        if (uri.startsWith("/hardcoded/")) {
	        	writeHardCodedResponse(e);
	        } else if (uri.startsWith("/files/")) {
	        	retrieveStaticFileAndWriteResponse(uri.substring(7), ctx, e);
	        } else if (uri.startsWith("/uploads/") && request.getMethod() == POST) {
	        	beginUpload(ctx, e);
	        } else if (uri.startsWith("/snoop/")) {
	        	writeSnoopResponse(e);
	        } else {
	        	sendError(ctx, NOT_FOUND);
	        }

		} else {
        	handleUploadStream(ctx, e);
		}

    }

	private void handleUploadStream(ChannelHandlerContext ctx, MessageEvent e) {
        // New chunk is received
        HttpChunk chunk = (HttpChunk) e.getMessage();
        try {
            decoder.offer(chunk);
        } catch (ErrorDataDecoderException e1) {
            e1.printStackTrace();
            Channels.close(e.getChannel());
            return;
        }
        // example of reading chunk by chunk (minimize memory usage due to Factory)
        readHttpDataChunkByChunk(e.getChannel());
        // example of reading only if at the end
        if (chunk.isLast()) {
            readHttpDataAllReceive(e.getChannel());
            readingChunks = false;
            writeResponse(e.getChannel());
        }
    }

	private void beginUpload(ChannelHandlerContext ctx, MessageEvent e) {
        // clean previous FileUpload if Any
        if (decoder != null) {
            decoder.cleanFiles();
            decoder = null;
        }

        HttpRequest request = (HttpRequest) e.getMessage();

        // if GET Method: should not try to create a HttpPostRequestDecoder
        try {
            decoder = new HttpPostRequestDecoder(factory, request);
        } catch (ErrorDataDecoderException e1) {
            e1.printStackTrace();
            Channels.close(e.getChannel());
            return;
        } catch (IncompatibleDataDecoderException e1) {
            // GET Method: should not try to create a HttpPostRequestDecoder
            // So OK but stop here
            return;
        }

        if (request.isChunked()) {
            // Chunk version
            readingChunks = true;
        } else {
            // Not chunk version
            readHttpDataAllReceive(e.getChannel());
            writeResponse(e.getChannel());
        }
	}

    private void writeResponse(Channel channel) {
        // Convert the response content to a ChannelBuffer.
        ChannelBuffer buf = ChannelBuffers.copiedBuffer("File upload completed!\n\n", CharsetUtil.UTF_8);

        // Decide whether to close the connection or not.
        boolean close = HttpHeaders.Values.CLOSE.equalsIgnoreCase(request
                .getHeader(HttpHeaders.Names.CONNECTION)) ||
                request.getProtocolVersion().equals(HttpVersion.HTTP_1_0) &&
                !HttpHeaders.Values.KEEP_ALIVE.equalsIgnoreCase(request
                        .getHeader(HttpHeaders.Names.CONNECTION));

        // Build the response object.
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK);
        response.setContent(buf);
        response.setHeader(HttpHeaders.Names.CONTENT_TYPE,
                "text/plain; charset=UTF-8");

        // Write the response.
        ChannelFuture future = channel.write(response);
        // Close the connection after the write operation is done if necessary.
        if (close) {
            future.addListener(ChannelFutureListener.CLOSE);
        }

    }

    /**
     * Example of reading all InterfaceHttpData from finished transfer
     *
     * @param channel
     */
    private void readHttpDataAllReceive(Channel channel) {
        List<InterfaceHttpData> datas = null;
        try {
            datas = decoder.getBodyHttpDatas();
        } catch (NotEnoughDataDecoderException e1) {
            // Should not be!
            e1.printStackTrace();
            Channels.close(channel);
            return;
        }
        for (InterfaceHttpData data: datas) {
            writeHttpData(data);
        }
    }

    /**
     * Example of reading request by chunk and getting values from chunk to
     * chunk
     *
     * @param channel
     */
    private void readHttpDataChunkByChunk(Channel channel) {
        try {
            while (decoder.hasNext()) {
                InterfaceHttpData data = decoder.next();
                if (data != null) {
                    // new value
                    writeHttpData(data);
                }
            }
        } catch (EndOfDataDecoderException e1) {
            // end
        }
    }

	private void writeHttpData(InterfaceHttpData data) {
        if (data.getHttpDataType() == HttpDataType.FileUpload) {
            FileUpload fileUpload = (FileUpload) data;
            if (fileUpload.isCompleted()) {

            	try {
	            	File saveTo = File.createTempFile(fileUpload.getFilename() + '-', null, new File(uploadsDir));
	            	fileUpload.renameTo(saveTo);
            	} catch (IOException e) {
            		// do nothing
            		e.printStackTrace();
            	} finally {
                	decoder.removeHttpDataFromClean(fileUpload);
            	}

            }
        }
	    
    }

	private void writeHardCodedResponse(MessageEvent e) {
		writeResponse((HttpRequest) e.getMessage(),
				"<html><body><h1>Olá Mundo!!!</h1></body></html>",
				e.getChannel());
	}

	private void writeSnoopResponse(MessageEvent e) {
		// TODO Auto-generated method stub
		
	}

	private void retrieveStaticFileAndWriteResponse(String uri, ChannelHandlerContext ctx, MessageEvent e) throws Exception {

    	// Convert file separators.
        uri = uri.replace('/', File.separatorChar);

        // Convert to absolute path.
        final String path = getDocumentPath(uri);

        File file = new File(path);
        if (file.isHidden() || !file.exists()) {
            sendError(ctx, NOT_FOUND);
            return;
        }
        if (!file.isFile()) {
            sendError(ctx, FORBIDDEN);
            return;
        }

        HttpRequest request = (HttpRequest) e.getMessage();

        // Cache Validation
        String ifModifiedSince = request.getHeader(HttpHeaders.Names.IF_MODIFIED_SINCE);
        if (ifModifiedSince != null && !ifModifiedSince.equals("")) {
            SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
            Date ifModifiedSinceDate = dateFormatter.parse(ifModifiedSince);

            // Only compare up to the second because the datetime format we send to the client does not have milliseconds 
            long ifModifiedSinceDateSeconds = ifModifiedSinceDate.getTime() / 1000;
            long fileLastModifiedSeconds = file.lastModified() / 1000;
            if (ifModifiedSinceDateSeconds == fileLastModifiedSeconds) {
                sendNotModified(ctx);
                return;
            }
        }
        
        RandomAccessFile raf;
        try {
            raf = new RandomAccessFile(file, "r");
        } catch (FileNotFoundException fnfe) {
            sendError(ctx, NOT_FOUND);
            return;
        }
        long fileLength = raf.length();

        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
        setContentLength(response, fileLength);
        setContentTypeHeader(response, file);
        setServerHeader(response);
        setDateAndCacheHeaders(response, file);
        
        Channel ch = e.getChannel();

        // Write the initial line and the header.
        ch.write(response);

        // Write the content.
        ChannelFuture writeFuture;
        if (useZeroCopy(ch)) {
            // Cannot use zero-copy with HTTPS.
            writeFuture = ch.write(new ChunkedFile(raf, 0, fileLength, 8192));
        } else {
            // No encryption - use zero-copy.
            final FileRegion region =
                new DefaultFileRegion(raf.getChannel(), 0, fileLength);
            writeFuture = ch.write(region);
            writeFuture.addListener(new ChannelFutureProgressListener() {
                @Override
                public void operationComplete(ChannelFuture future) {
                    region.releaseExternalResources();
                }

                @Override
                public void operationProgressed(
                        ChannelFuture future, long amount, long current, long total) {
                    System.out.printf("%s: %d / %d (+%d)%n", path, current, total, amount);
                }
            });
        }

        // Decide whether to close the connection or not.
        if (!isKeepAlive(request)) {
            // Close the connection when the whole content is written out.
            writeFuture.addListener(ChannelFutureListener.CLOSE);
        }

	}

	protected boolean useZeroCopy(Channel ch) {
		if (useZeroCopy == null) {
			useZeroCopy = (ch.getPipeline().get(SslHandler.class) != null);
		}
		return useZeroCopy;
	}

	public void dontUseZeroCopy() {
		this.useZeroCopy = Boolean.FALSE;
	}

	private String getDocumentPath(String uri) {
		return docRoot + File.separator + uri;
	}

    private void writeResponse(HttpRequest request, String responseContent, Channel ch) {
        // Decide whether to close the connection or not.
        boolean keepAlive = isKeepAlive(request);

        // Build the response object.
        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
        response.setHeader(CONTENT_TYPE, "text/html; charset=utf-8");
        setServerHeader(response);
        setDateHeader(response);
        response.setContent(ChannelBuffers.copiedBuffer(responseContent, CharsetUtil.UTF_8));

        if (keepAlive) {
            // Add 'Content-Length' header only for a keep-alive connection.
        	setContentLength(response, response.getContent().readableBytes());
        }

        // Encode the cookie.
        String cookieString = request.getHeader(COOKIE);
        if (cookieString != null) {
            CookieDecoder cookieDecoder = new CookieDecoder();
            Set<Cookie> cookies = cookieDecoder.decode(cookieString);
            if (!cookies.isEmpty()) {
                // Reset the cookies if necessary.
                CookieEncoder cookieEncoder = new CookieEncoder(true);
                for (Cookie cookie : cookies) {
                    cookieEncoder.addCookie(cookie);
                }
                response.addHeader(SET_COOKIE, cookieEncoder.encode());
            }
        }

        // Write the response.
        ChannelFuture writeFuture = ch.write(response);

        // Close the non-keep-alive connection after the write operation is done.
        if (!keepAlive) {
            writeFuture.addListener(ChannelFutureListener.CLOSE);
        }
    }

	@Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
    		throws Exception {

        Channel ch = e.getChannel();
        Throwable cause = e.getCause();
        if (cause instanceof TooLongFrameException) {
            sendError(ctx, BAD_REQUEST);
            return;
        }

        cause.printStackTrace();
        if (ch.isConnected()) {
            sendError(ctx, INTERNAL_SERVER_ERROR);
        }

    }

    /**
     * When file timestamp is the same as what the browser is sending up, send a "304 Not Modified"
     * 
     * @param ctx
     *            Context
     */
    private void sendNotModified(ChannelHandlerContext ctx) {
        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.NOT_MODIFIED);
        setServerHeader(response);
        setDateHeader(response);

        // Close the connection as soon as the error message is sent.
        ctx.getChannel().write(response).addListener(ChannelFutureListener.CLOSE);
    }

    private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, status);
        response.setHeader(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=UTF-8");
        setServerHeader(response);
        response.setContent(ChannelBuffers.copiedBuffer(
                "Failure: " + status.toString() + "\r\n",
                CharsetUtil.UTF_8));

        // Close the connection as soon as the error message is sent.
        ctx.getChannel().write(response).addListener(ChannelFutureListener.CLOSE);
    }

    private String sanitizeUri(String uri) {
    	if (uri == null || "".equals(uri)) {
    		return null;
    	}

        // Decode the path.
        try {
            uri = URLDecoder.decode(uri, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            try {
                uri = URLDecoder.decode(uri, "ISO-8859-1");
            } catch (UnsupportedEncodingException e1) {
                throw new Error();
            }
        }

        // Convert to absolute path.
        return uri;
    }

    /**
     * Sets the Date header for the HTTP response
     * 
     * @param response
     *            HTTP response
     */
    private void setDateHeader(HttpResponse response) {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
        dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

        Calendar time = new GregorianCalendar();
        response.setHeader(HttpHeaders.Names.DATE, dateFormatter.format(time.getTime()));
    }

    private void setServerHeader(HttpResponse response) {
        response.setHeader(SERVER, "rafaelmarins.com/Benchmark_v1.0.0 Netty.io/3.3.1.Final");
    }

    /**
     * Sets the Date and Cache headers for the HTTP Response
     * 
     * @param response
     *            HTTP response
     * @param fileToCache
     *            file to extract content type
     */
    private void setDateAndCacheHeaders(HttpResponse response, File fileToCache) {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
        dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

        // Date header
        Calendar time = new GregorianCalendar();
        response.setHeader(HttpHeaders.Names.DATE, dateFormatter.format(time.getTime()));

        // Add cache headers
        time.add(Calendar.SECOND, HTTP_CACHE_SECONDS);
        response.setHeader(HttpHeaders.Names.EXPIRES, dateFormatter.format(time.getTime()));
        response.setHeader(HttpHeaders.Names.CACHE_CONTROL, "private, max-age=" + HTTP_CACHE_SECONDS);
        response.setHeader(HttpHeaders.Names.LAST_MODIFIED, dateFormatter.format(new Date(fileToCache.lastModified())));
    }

    /**
     * Sets the content type header for the HTTP Response
     * 
     * @param response
     *            HTTP response
     * @param file
     *            file to extract content type
     */
    private void setContentTypeHeader(HttpResponse response, File file) {
        MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
        response.setHeader(HttpHeaders.Names.CONTENT_TYPE, mimeTypesMap.getContentType(file.getPath()));
    }

}
