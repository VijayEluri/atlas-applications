package org.atlasapi.application;

import static com.metabroadcast.common.persistence.mongo.MongoBuilders.where;
import static com.metabroadcast.common.persistence.mongo.MongoConstants.NO_UPSERT;
import static com.metabroadcast.common.persistence.mongo.MongoConstants.SINGLE;
import static org.atlasapi.application.ApplicationConfigurationTranslator.PUBLISHER_KEY;
import static org.atlasapi.application.ApplicationConfigurationTranslator.SOURCES_KEY;
import static org.atlasapi.application.ApplicationConfigurationTranslator.STATE_KEY;
import static org.atlasapi.application.OldApplicationTranslator.APPLICATION_CONFIG_KEY;

import java.util.Set;

import org.atlasapi.application.OldApplication;
import org.atlasapi.application.SourceStatus.SourceState;
import org.atlasapi.application.users.User;
import org.atlasapi.media.entity.Publisher;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.metabroadcast.common.persistence.mongo.DatabasedMongo;
import com.metabroadcast.common.persistence.mongo.MongoConstants;
import com.metabroadcast.common.text.MoreStrings;
import com.mongodb.CommandResult;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.ReadPreference;

public class OldMongoApplicationStore implements OldApplicationStore {
	
	public static final String APPLICATION_COLLECTION = "applications";
	
	private final OldApplicationTranslator translator = new OldApplicationTranslator();
	
	private final DBCollection applications;

	private DatabasedMongo mongo;

    private final Function<DBObject, OldApplication> translatorFunction = new Function<DBObject, OldApplication>(){
        @Override
        public OldApplication apply(DBObject dbo) {
            return translator.fromDBObject(dbo);
        }
    };
	
	public OldMongoApplicationStore(DatabasedMongo mongo) {
		this.applications = mongo.collection(APPLICATION_COLLECTION);
		this.applications.setReadPreference(ReadPreference.primary());
		this.mongo = mongo;
	}
	
	@Override
	public Optional<OldApplication> applicationForKey(String key) {
		String apiKeyField = String.format("%s.%s", OldApplicationTranslator.APPLICATION_CREDENTIALS_KEY, OldApplicationCredentialsTranslator.API_KEY_KEY);
		return Optional.fromNullable(translator.fromDBObject(applications.findOne(where().fieldEquals(apiKeyField, key).build())));
	}

	@Override
	public Optional<OldApplication> applicationFor(String slug) {
		return Optional.fromNullable(translator.fromDBObject(applications.findOne(where().idEquals(slug).build())));
	}

	@Override
	public OldApplication persist(OldApplication application) {
		applications.insert(translator.toDBObject(application));
		CommandResult result = mongo.database().getLastError();
		if (result.get("err") != null && result.getInt("code") == 11000) {
			throw new IllegalArgumentException("Duplicate application slug");
		}
		return application;
	}
	
	@Override
	public OldApplication update(OldApplication application) {
		applications.update(where().idEquals(application.getSlug()).build(), translator.toDBObject(application), NO_UPSERT, SINGLE);
		return application;
	}

    @Override
    public Set<OldApplication> applicationsFor(Optional<User> user) {
        if (!user.isPresent()) {
            return ImmutableSet.of();
        }
        return ImmutableSet.copyOf(Iterables.transform(applications.find(where().idIn(user.get().getApplications()).build()), translatorFunction));
    }

    @Override
    public Set<OldApplication> applicationsFor(Publisher source) {
        String sourceField = String.format("%s.%s.%s", APPLICATION_CONFIG_KEY, SOURCES_KEY, PUBLISHER_KEY);
        String stateField =  String.format("%s.%s.%s", APPLICATION_CONFIG_KEY, SOURCES_KEY, STATE_KEY);
        return ImmutableSet.copyOf(Iterables.transform(applications.find(where().fieldEquals(sourceField, source.key()).fieldIn(stateField, states()).build()), translatorFunction));
    }

    private Iterable<String> states() {
        return Iterables.transform(ImmutableSet.of(SourceState.AVAILABLE, SourceState.REQUESTED), Functions.compose(MoreStrings.TO_LOWER, Functions.toStringFunction()));
    }

    @Override
    public Iterable<OldApplication> allApplications() {
        return Iterables.transform(applications.find(where().build()), translatorFunction);
    }
	
}
