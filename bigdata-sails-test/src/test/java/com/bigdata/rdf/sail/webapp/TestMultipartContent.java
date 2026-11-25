package com.bigdata.rdf.sail.webapp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import junit.framework.TestCase;

import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.entity.mime.FormBodyPart;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.message.BasicNameValuePair;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.io.Content;

import com.bigdata.rdf.sail.webapp.client.EntityContentProvider;

/**
 * The Jetty HttpClient does not provide "out of the box" support for multipart
 * content similar to the Apache MultipartEntity.
 * <p>
 * This unit test confirms that the new MultipartContentProvider provides the
 * equivalent functionality.
 * 
 * @author Martyn Cutcher
 *
 */
public class TestMultipartContent extends TestCase {

	public void testSimpleFormContent() throws UnsupportedEncodingException {
		
    	final List<NameValuePair> formparams = new ArrayList<NameValuePair>();
    	
    	formparams.add(new BasicNameValuePair("First Name", "Martyn"));
    	formparams.add(new BasicNameValuePair("Last Name", "Cutcher"));
     	
    	final HttpEntity entity =  new UrlEncodedFormEntity(formparams, "UTF-8");
    	
    	final Request.Content content = new EntityContentProvider(entity);
    	
    	System.out.println(new String(entity.getContentType().toString()));
	}
	
	private HttpEntity getUpdateEntity() {
		final Random r = new Random();
		final byte[] data = new byte[256];
		
        final MultipartEntity entity = new MultipartEntity();
        r.nextBytes(data);
        entity.addPart(new FormBodyPart("remove", 
                new ByteArrayBody(
                        data, 
                        "application/xml", 
                        "remove")));
        r.nextBytes(data);
        entity.addPart(new FormBodyPart("add", 
                new ByteArrayBody(
                        data, 
                        "application/xml", 
                        "add")));
		
        return entity;
	}
	
	public void testApacheUpdate() throws UnsupportedOperationException, IOException {
		final HttpEntity entity = getUpdateEntity();
		
        ByteArrayOutputStream outstr = new ByteArrayOutputStream();
        entity.writeTo(outstr);
        
        System.out.println(new String(outstr.toByteArray()));
	}
	
	public void testJettyUpdate() throws UnsupportedOperationException, IOException {
		final HttpEntity entity = getUpdateEntity();
		
        final EntityContentProvider cp = new EntityContentProvider(entity);

        final ByteBuffer bb = Content.Source.asByteBuffer(cp);
        final byte[] bytes = new byte[bb.remaining()];
        bb.get(bytes);
        System.out.println(new String(bytes));
	}
	
	void display(Request.Content cp) throws IOException {
		System.out.println(Content.Source.asString(cp));
	}
		
}
