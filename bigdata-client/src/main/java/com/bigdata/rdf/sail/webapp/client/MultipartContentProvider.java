/**

Copyright (C) SYSTAP, LLC DBA Blazegraph 2006-2016.  All rights reserved.

Contact:
     SYSTAP, LLC DBA Blazegraph
     2501 Calvert ST NW #106
     Washington, DC 20008
     licenses@blazegraph.com

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; version 2 of the License.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/
package com.bigdata.rdf.sail.webapp.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Random;

import org.eclipse.jetty.client.ByteBufferRequestContent;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.io.Content;

import com.bigdata.util.BytesUtil;

public class MultipartContentProvider implements Request.Content {
	
	final String m_boundary;
	
	final ByteArrayOutputStream m_data = new ByteArrayOutputStream();
	
	final Writer m_writer = new OutputStreamWriter(m_data);
	
	/** Snapshot of the parts, taken when the body is first read. */
	private Request.Content m_content = null;
	
	public MultipartContentProvider() {
		m_boundary = BytesUtil.toHexString(new int[] {new Random().nextInt()});
		
		try {
			m_writer.write("Content-Type: multipart/mixed; boundary=" + m_boundary + "\n");
		} catch (IOException e) {
			throw new RuntimeException("Unexpected", e);
		}
	}

	private synchronized Request.Content content() {
		if (m_content == null) {
			m_content = new ByteBufferRequestContent(ByteBuffer.wrap(m_data.toByteArray()));
		}
		return m_content;
	}

	@Override
	public Content.Chunk read() {
		return content().read();
	}

	@Override
	public void demand(final Runnable demandCallback) {
		content().demand(demandCallback);
	}

	@Override
	public void fail(final Throwable failure) {
		content().fail(failure);
	}

	@Override
	public void fail(final Throwable failure, final boolean last) {
		content().fail(failure, last);
	}

	@Override
	public boolean rewind() {
		return content().rewind();
	}

	@Override
	public long getLength() {
		return m_data.size();
	}
	
	/*
	 *    --AaB03x
   	 *	Content-Disposition: form-data; name="files"; filename="file1.txt"
   	 *	Content-Type: text/plain
	 */
	public void addPart(final String name, final Request.Content part, final String mimetype) {
		throw new UnsupportedOperationException();
	}

	public void addPart(String name, byte[] data, String mimetype) {
		try {
			m_writer.write("\n--" + m_boundary + "\n");
			m_writer.write("Content-Disposition: form-data;");
			m_writer.write(" name=\"" + name + "\"\n");
			m_writer.write("Content-Type: " + mimetype +"\n");
			m_writer.write("Content-Transfer-Encoding: base64\n\n");
			
			m_writer.write(Base64.getEncoder().encodeToString(data));
			m_writer.write("\n");
			
			m_writer.flush();
		} catch (IOException e) {
			throw new RuntimeException("Unexpected", e);
		}
	}

}
