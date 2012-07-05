package edu.uci.ics.asterix.feed.intake;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import com.sun.syndication.feed.synd.SyndEntryImpl;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.fetcher.FeedFetcher;
import com.sun.syndication.fetcher.FetcherEvent;
import com.sun.syndication.fetcher.FetcherListener;
import com.sun.syndication.fetcher.impl.FeedFetcherCache;
import com.sun.syndication.fetcher.impl.HashMapFeedInfoCache;
import com.sun.syndication.fetcher.impl.HttpURLFeedFetcher;

import edu.uci.ics.asterix.external.dataset.adapter.RSSFeedAdapter;
import edu.uci.ics.asterix.om.base.AMutableRecord;
import edu.uci.ics.asterix.om.base.AMutableString;
import edu.uci.ics.asterix.om.base.IAObject;
import edu.uci.ics.asterix.om.types.ARecordType;
import edu.uci.ics.asterix.om.types.BuiltinType;
import edu.uci.ics.asterix.om.types.IAType;

@SuppressWarnings("rawtypes")
public class RSSFeedClient extends PullBasedFeedClient {

	private final String feedURL;
	private long id = 0;
	private String id_prefix;
	private boolean feedModified = false;

	private String[] fieldNames = { "id", "title", "description", "link" };
	private IAType[] fieldTypes = { BuiltinType.ASTRING, BuiltinType.ASTRING,
			BuiltinType.ASTRING, BuiltinType.ASTRING };
	private Queue<SyndEntryImpl> rssFeedBuffer = new LinkedList<SyndEntryImpl>();

	IAObject[] mutableFields;

	private final FeedFetcherCache feedInfoCache;
	private final FeedFetcher fetcher;
	private final FetcherEventListenerImpl listener;
	private final URL feedUrl;
	String[] tupleFieldValues;

	public boolean isFeedModified() {
		return feedModified;
	}

	public void setFeedModified(boolean feedModified) {
		this.feedModified = feedModified;
	}

	public RSSFeedClient(RSSFeedAdapter adapter, String feedURL,
			String id_prefix) throws MalformedURLException {
		this.feedURL = feedURL;
		this.id_prefix = id_prefix;
		feedUrl = new URL(feedURL);
		feedInfoCache = HashMapFeedInfoCache.getInstance();
		fetcher = new HttpURLFeedFetcher(feedInfoCache);
		listener = new FetcherEventListenerImpl(this);
		fetcher.addFetcherEventListener(listener);

		mutableFields = new IAObject[] { new AMutableString(null),
				new AMutableString(null), new AMutableString(null),
				new AMutableString(null) };
		recordType = new ARecordType("FeedRecordType", fieldNames, fieldTypes,
				false);
		mutableRecord = new AMutableRecord(recordType, mutableFields);
		tupleFieldValues = new String[recordType.getFieldNames().length];
	}

	@Override
	public boolean setNextRecord() throws Exception {
		SyndEntryImpl feedEntry = getNextRSSFeed();
		if (feedEntry == null) {
			return false;
		}
		tupleFieldValues[0] = id_prefix + ":" + id;
		tupleFieldValues[1] = feedEntry.getTitle();
		tupleFieldValues[2] = feedEntry.getDescription().getValue();
		tupleFieldValues[3] = feedEntry.getLink();
		int numFields = recordType.getFieldNames().length;
		for (int i = 0; i < numFields; i++) {
			((AMutableString) mutableFields[i]).setValue(tupleFieldValues[i]);
			mutableRecord.setValueAtPos(i, mutableFields[i]);
		}
		id++;
		return true;
	}

	private SyndEntryImpl getNextRSSFeed() throws Exception {
		if (rssFeedBuffer.isEmpty()) {
			fetchFeed();
		}
		if (rssFeedBuffer.isEmpty()) {
			return null;
		} else {
			return rssFeedBuffer.remove();
		}
	}

	private void fetchFeed() {
		try {
			System.err.println("Retrieving feed " + feedURL);
			// Retrieve the feed.
			// We will get a Feed Polled Event and then a
			// Feed Retrieved event (assuming the feed is valid)
			SyndFeed feed = fetcher.retrieveFeed(feedUrl);
			if (feedModified) {
				System.err.println(feedUrl + " retrieved");
				System.err.println(feedUrl + " has a title: " + feed.getTitle()
						+ " and contains " + feed.getEntries().size()
						+ " entries.");

				List fetchedFeeds = feed.getEntries();
				rssFeedBuffer.addAll(fetchedFeeds);
			}
		} catch (Exception ex) {
			System.out.println("ERROR: " + ex.getMessage());
			ex.printStackTrace();
		}
	}

	@Override
	public void resetOnFailure(Exception e) {
		// TODO Auto-generated method stub

	}

}

class FetcherEventListenerImpl implements FetcherListener {

	private final IPullBasedFeedClient feedClient;

	public FetcherEventListenerImpl(IPullBasedFeedClient feedClient) {
		this.feedClient = feedClient;
	}

	/**
	 * @see com.sun.syndication.fetcher.FetcherListener#fetcherEvent(com.sun.syndication.fetcher.FetcherEvent)
	 */
	public void fetcherEvent(FetcherEvent event) {
		String eventType = event.getEventType();
		if (FetcherEvent.EVENT_TYPE_FEED_POLLED.equals(eventType)) {
			System.err.println("\tEVENT: Feed Polled. URL = "
					+ event.getUrlString());
		} else if (FetcherEvent.EVENT_TYPE_FEED_RETRIEVED.equals(eventType)) {
			System.err.println("\tEVENT: Feed Retrieved. URL = "
					+ event.getUrlString());
			((RSSFeedClient) feedClient).setFeedModified(true);
		} else if (FetcherEvent.EVENT_TYPE_FEED_UNCHANGED.equals(eventType)) {
			System.err.println("\tEVENT: Feed Unchanged. URL = "
					+ event.getUrlString());
			((RSSFeedClient) feedClient).setFeedModified(true);
		}
	}
}