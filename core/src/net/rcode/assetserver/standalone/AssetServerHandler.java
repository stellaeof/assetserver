package net.rcode.assetserver.standalone;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.rcode.assetserver.core.AssetLocator;
import net.rcode.assetserver.core.AssetRoot;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Jetty handler for implementing the asset server
 * 
 * @author stella
 *
 */
public class AssetServerHandler extends AbstractHandler {
	private static final Logger logger=LoggerFactory.getLogger(AssetServerHandler.class);
	
	private AssetRoot root;
	private boolean noCache=true;
	
	public AssetRoot getRoot() {
		return root;
	}
	
	public void setRoot(AssetRoot root) {
		this.root = root;
	}
	
	public boolean isNoCache() {
		return noCache;
	}
	public void setNoCache(boolean noCache) {
		this.noCache = noCache;
	}
	
	@Override
	public void handle(String target, Request baseRequest, HttpServletRequest request,
			HttpServletResponse response) throws IOException, ServletException {
		if (root==null) {
			// Not found
			baseRequest.setHandled(false);
			return;
		}
		
		AssetLocator locator;
		
		try {
			locator=root.resolve(request.getRequestURI());
		} catch (ServletException e) {
			throw e;
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			logger.warn("Uncaught exception while processing request for " + request.getRequestURI(), e);
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Uncaught exception while processing request: " + e.getMessage());
			return;
		}
		
		if (locator==null) {
			// Not found
			baseRequest.setHandled(false);
			return;
		}
		
		// If here, then the path belongs to a mount
		String contentType=locator.getContentType(), encoding=locator.getCharacterEncoding();
		if (contentType!=null) {
			response.setContentType(contentType);
		}
		if (encoding!=null) {
			response.setCharacterEncoding(encoding);
		}
		
		baseRequest.setHandled(true);
		response.setStatus(200);
		if (noCache) {
			response.addHeader("Expires", "Fri, 30 Oct 1998 14:19:41 GMT");
			response.addHeader("Cache-Control", "max-age=0, no-cache");
		}
		locator.writeTo(response.getOutputStream());
	}

}