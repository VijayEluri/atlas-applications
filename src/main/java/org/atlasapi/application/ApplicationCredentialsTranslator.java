package org.atlasapi.application;

import org.atlasapi.application.ApplicationCredentials;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.metabroadcast.common.net.IpRange;
import com.metabroadcast.common.persistence.translator.TranslatorUtils;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

public class ApplicationCredentialsTranslator {

	//public static final Pattern IP_ADDRESS_PATTERN = Pattern.compile("\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b");
	
	private static final Function<String, IpRange> TO_IP_RANGE = new Function<String, IpRange>(){
    	@Override
    	public IpRange apply(String address) {
    		return IpRange.fromString(address);
    	}
    };
    public static final String IP_ADDRESS_KEY = "ip_addresses";
	public static final String API_KEY_KEY = "api_key";
	public static final String ENABLED = "enabled";

	public DBObject toDBObject(ApplicationCredentials credentials) {
		DBObject dbo = new BasicDBObject();

		TranslatorUtils.from(dbo, API_KEY_KEY, credentials.getApiKey());
		if (credentials.getIpAddressRanges() != null) {
			TranslatorUtils.fromList(dbo, ImmutableList.copyOf(Iterables.transform(credentials.getIpAddressRanges(), new Function<IpRange, String>(){
	
				@Override
				public String apply(IpRange range) {
					return range.toFriendlyString();
				}
				
			})), IP_ADDRESS_KEY);
		}
		
		TranslatorUtils.from(dbo, ENABLED, credentials.isEnabled());
		
		return dbo;
	}
	
	public ApplicationCredentials fromDBObject(DBObject dbo) {
		// Set enabled to true if entry missing for backward compatibility
		boolean enabled = true;
		if (dbo.containsField(ENABLED)) {
			enabled = TranslatorUtils.toBoolean(dbo, ENABLED);
		}
		ApplicationCredentials credentials = new ApplicationCredentials(TranslatorUtils.toString(dbo, API_KEY_KEY), enabled);
		
		return credentials.copyWithIpAddresses(Iterables.filter(Iterables.transform(TranslatorUtils.toList(dbo, IP_ADDRESS_KEY), TO_IP_RANGE), Predicates.notNull()));
	}
}
