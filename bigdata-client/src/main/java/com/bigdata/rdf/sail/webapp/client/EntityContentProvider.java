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
/*
 * Created on Oct 13, 2011
 */
package com.bigdata.rdf.sail.webapp.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.eclipse.jetty.client.BytesRequestContent;
import org.eclipse.jetty.client.InputStreamRequestContent;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.io.Content;

/**
 * Adapts an Apache {@link HttpEntity} as the body of a Jetty client
 * {@link Request}.
 * <p>
 * A streaming entity is sent from its input stream; otherwise the entity is
 * buffered. In both cases the declared length is the entity's content length.
 */
public class EntityContentProvider implements Request.Content {

	private final HttpEntity m_entity;

	private final Request.Content m_content;

	public EntityContentProvider(final HttpEntity entity) {
		m_entity = entity;
		final String contentType = getContentType();
		try {
			if (m_entity.isStreaming()) {
				m_content = new InputStreamRequestContent(contentType, m_entity.getContent());
			} else {
				// If Apache Entity does not stream then we need to double buffer to an output stream - not ideal
				final ByteArrayOutputStream streambuf = new ByteArrayOutputStream();
				m_entity.writeTo(streambuf);
				m_content = new BytesRequestContent(contentType, streambuf.toByteArray());
			}
		} catch (IOException e) {
			throw new RuntimeException("Unexpected", e);
		}
	}

	@Override
	public long getLength() {
		return m_entity.getContentLength();
	}

	@Override
	public String getContentType() {
		final Header h = m_entity.getContentType();
		return h == null ? null : h.getValue();
	}

	@Override
	public Content.Chunk read() {
		return m_content.read();
	}

	@Override
	public void demand(final Runnable demandCallback) {
		m_content.demand(demandCallback);
	}

	@Override
	public void fail(final Throwable failure) {
		m_content.fail(failure);
	}

	@Override
	public void fail(final Throwable failure, final boolean last) {
		m_content.fail(failure, last);
	}

	@Override
	public boolean rewind() {
		return m_content.rewind();
	}

}
