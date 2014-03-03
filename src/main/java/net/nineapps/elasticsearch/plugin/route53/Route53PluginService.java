package net.nineapps.elasticsearch.plugin.route53;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.collect.Lists;
import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.route53.AmazonRoute53;
import com.amazonaws.services.route53.AmazonRoute53Client;
import com.amazonaws.services.route53.model.Change;
import com.amazonaws.services.route53.model.ChangeAction;
import com.amazonaws.services.route53.model.ChangeBatch;
import com.amazonaws.services.route53.model.ChangeInfo;
import com.amazonaws.services.route53.model.ChangeResourceRecordSetsRequest;
import com.amazonaws.services.route53.model.ListResourceRecordSetsRequest;
import com.amazonaws.services.route53.model.RRType;
import com.amazonaws.services.route53.model.ResourceRecord;
import com.amazonaws.services.route53.model.ResourceRecordSet;

public class Route53PluginService extends AbstractLifecycleComponent<Route53PluginService> {
	private String hostedZoneName;
	private String hostedZoneId;
    private AWSCredentials awsCredentials;
    private AmazonRoute53 route53;
    private String clusterName;
	
	@Inject
	public Route53PluginService(Settings settings) {
		super(settings);
        String accessKey = settings.get("route53.aws.access_key");
        String secretKey = settings.get("route53.aws.secret_key");
        
        if (accessKey == null || accessKey.equals("") || secretKey == null || secretKey.equals("")) {
        	logger.debug("access_key or secret_key not found or empty. Trying access_key_id and secret_access_key...");
            accessKey = settings.get("route53.aws.access_key_id");
            secretKey = settings.get("route53.aws.secret_access_key");
        }
        
        logger.debug("access key configured is ", accessKey);
        awsCredentials = new BasicAWSCredentials(accessKey, secretKey);

//        String region = figureOutRegion(awsCredentials);
//        logger.info("configured region is [{}]",region);
        route53 = route53Client();
        
        hostedZoneName = settings.get("route53.hosted_zone");
        hostedZoneId = settings.get("route53.hosted_zone_id");

        clusterName = settings.get("cluster.name");
        logger.info("cluster name is [{}]", clusterName);
	}

//	private String figureOutRegion(AWSCredentials awsCredentials) {
//		AmazonEC2 ec2 = new AmazonEC2Client(awsCredentials);
//		List<AvailabilityZone> availabilityZones = ec2.describeAvailabilityZones().getAvailabilityZones();
//		logger.debug("availability zones are ", availabilityZones.size());
//		logger.debug("availability zones are ", availabilityZones);
//		// TODO
//		String region = "sarasa";
//        for(AvailabilityZone az : availabilityZones){
//        	region = az.getRegionName();
//            logger.debug("regionName: " +az.getRegionName());
//            logger.debug("zoneName: "+az.getZoneName());
//        }
//        return region;
//	}
	
	@Override
	protected void doClose() throws ElasticsearchException {		
        logger.info("Closing Route53 plugin");
		
        String publicHostName = retrievePublicHostName();
        String recordName = getRecordName();
        String hostName = retrieveHostName();

        if (recordExists(recordName, hostName)) {
        
	        ResourceRecordSet recordSet = recordSet(publicHostName, hostName,
					recordName);
	
	        Change change = new Change(ChangeAction.DELETE, recordSet);
	        ChangeResourceRecordSetsRequest request = new ChangeResourceRecordSetsRequest(hostedZoneId, new ChangeBatch(Lists.newArrayList(change)));
	        try {
		        ChangeInfo info = route53.changeResourceRecordSets(request).getChangeInfo();
		        logger.info("ChangeInfo comment: [{}], id: [{}], status: [{}]", info.getComment(), info.getId(), info.getStatus());
	        } catch(Exception e) {
	        	logger.error("Exception when removing record", e);
	        }
	        
	        logger.info("Route53 record has been deleted");
        } else {
        	logger.info("Route53 didn't exist, not doing anything.");
        }
	}

	@Override
	protected void doStart() throws ElasticsearchException {
        logger.info("Assigning Route53 record for hosted zone name [{}]", hostedZoneName);
        String publicHostName = retrievePublicHostName();
        String hostName = retrieveHostName();

        String recordName = getRecordName();
        
        if (recordExists(recordName, hostName)) {
        	logger.info("Route53 record already exists, so no need to add it...");
        } else {
        
	        ResourceRecordSet recordSet = recordSet(publicHostName, hostName,
					recordName);
	        Change change = new Change(ChangeAction.CREATE, recordSet);
	        ChangeResourceRecordSetsRequest request = new ChangeResourceRecordSetsRequest(hostedZoneId, new ChangeBatch(Lists.newArrayList(change)));
	        route53.changeResourceRecordSets(request);
	        
	        logger.info("Created Route53 record");
        }
	}

	private ResourceRecordSet recordSet(String publicHostName, String hostName,
			String recordName) {
		ResourceRecordSet recordSet = new ResourceRecordSet(recordName, RRType.CNAME);
		recordSet.setTTL(60l);
		
		ResourceRecord record = new ResourceRecord(publicHostName);
		recordSet.setResourceRecords(Lists.newArrayList(record));
		recordSet.setSetIdentifier(hostName);
		recordSet.setWeight(100l);
		return recordSet;
	}

	private String getRecordName() {
		String recordName = clusterName+"."+hostedZoneName+".";
		return recordName;
	}

	private boolean recordExists(String recordName, String identifier) {
        ListResourceRecordSetsRequest listRequest = new ListResourceRecordSetsRequest(hostedZoneId);
        listRequest.setStartRecordName(recordName);
        listRequest.setStartRecordType(RRType.CNAME.toString());
        List<ResourceRecordSet> records = route53.listResourceRecordSets(listRequest).getResourceRecordSets();
        for (ResourceRecordSet resourceRecordSet : records) {
			if (!recordName.equals(resourceRecordSet.getName())) {
				break;
			} else {
				if (RRType.CNAME.toString().equals(resourceRecordSet.getType()) &&
						identifier.equals(resourceRecordSet.getSetIdentifier())) {
					return true;
				}
			}
		}
        return false;
	}
	
	private String retrievePublicHostName() {
		String publicHostName;
		try {
			publicHostName = retrieveMetadata("public-hostname");
		} catch (Exception e) {
			throw new ElasticsearchException("Could not retrieve the EC2 instance's public host name, so could not finish with my mission.", e);
		}
        logger.debug("public host name is [{}]", publicHostName);
		return publicHostName;
	}

	private String retrieveHostName() {
		String hostName;
		try {
			hostName = retrieveMetadata("hostname");
		} catch (Exception e) {
			throw new ElasticsearchException("Could not retrieve the EC2 instance's host name, so could not finish with my mission.", e);
		}
        logger.debug("host name is [{}]", hostName);
		return hostName;
	}

	@Override
	protected void doStop() throws ElasticsearchException {
        logger.info("Stopping Route53 plugin");
	}
	
	private AmazonRoute53 route53Client() {
		return new AmazonRoute53Client(awsCredentials);
	}
	
	private String retrieveMetadata(String name) throws Exception {
		String metadata = "";
		String inputLine;
		URL EC2MetaData;
		try {
			EC2MetaData = new URL("http://169.254.169.254/latest/meta-data/" + name);
			URLConnection metadataConnection = EC2MetaData.openConnection();
			BufferedReader in = new BufferedReader(
			new InputStreamReader(
					metadataConnection.getInputStream()));
			while ((inputLine = in.readLine()) != null)
			{	
				metadata = inputLine;
			}
			in.close();
			return metadata;
		} catch (IOException e) {
			logger.error("Unable to retrieve metadata [{}].", e, name);
			throw e;
		}	}

}
