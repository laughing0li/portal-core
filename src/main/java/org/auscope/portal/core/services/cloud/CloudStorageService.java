package org.auscope.portal.core.services.cloud;

import java.io.File;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.auscope.portal.core.cloud.CloudFileInformation;
import org.auscope.portal.core.cloud.CloudFileOwner;
import org.auscope.portal.core.cloud.CloudJob;
import org.auscope.portal.core.services.PortalServiceException;
import org.auscope.portal.core.util.TextUtil;
import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.KeyNotFoundException;
import org.jclouds.blobstore.domain.Blob;
import org.jclouds.blobstore.domain.PageSet;
import org.jclouds.blobstore.domain.StorageMetadata;
import org.jclouds.blobstore.domain.internal.BlobMetadataImpl;
import org.jclouds.blobstore.domain.internal.MutableBlobMetadataImpl;
import org.jclouds.blobstore.options.ListContainerOptions;
import org.jclouds.domain.Credentials;
import org.jclouds.io.ContentMetadata;
import org.jclouds.rest.AuthorizationException;
import org.jclouds.sts.STSApi;
import org.jclouds.sts.domain.UserAndSessionCredentials;
import org.jclouds.sts.options.AssumeRoleOptions;

import com.google.common.base.Supplier;

/**
 * Service for providing storage of objects (blobs) in a cloud using the JClouds library
 *
 * @author Josh Vote
 *
 */
public class CloudStorageService {

    /** The bucket name used when no bucket is specified */
    public static final String DEFAULT_BUCKET = "portal-core-storage-service";

    private final Log log = LogFactory.getLog(getClass());

    /** Prefix to apply to any job files stored (will be appended with job id) - defaults to hostname */
    protected String jobPrefix;

    /** The unique ID for this service - use it for distinguishing this service from other instances of this class - can be null or empty */
    private String id;
    /** A short descriptive name for human identification of this service */
    private String name;
    /** The authentication version to use when connecting to this object store - can be null or empty */
    private String authVersion;
    /**
     * The region identifier string for this service (if any). Can be null/empty. Currently this field is NON functional, it is only for descriptive purposes
     * due to limitations in JClouds.
     */
    private String regionName;
    /** Username credential for accessing the storage service */
    private String accessKey;
    /** Password credentials for accessing the storage service */
    private String secretKey;
    /** A unique identifier identifying the type of storage API used to store this job's files - eg 'swift' */
    private String provider;
    /** The URL endpoint for the cloud storage service */
    private String endpoint;

    /**
     * The bucket that this service will access - defaults to DEFAULT_BUCKET
     */
    private String bucketPrefix = DEFAULT_BUCKET;

	private boolean relaxHostName;

	private boolean stripExpectHeader;

	private BlobStoreContext mockBlobStoreContext = null; // Set for unit test only

    /**
     * Creates a new instance for connecting to the specified parameters
     * 
     * @param endpoint
     *            The URL endpoint for the cloud storage service
     * @param provider
     *            A unique identifier identifying the type of storage API used to store this job's files - eg 'swift'
     * @param accessKey
     *            Username credential for accessing the storage service
     * @param secretKey
     *            Password credentials for accessing the storage service
     */
    public CloudStorageService(String provider, String accessKey, String secretKey) {
        this(null, provider, accessKey, secretKey, null, false);
    }

    /**
     * Creates a new instance for connecting to the specified parameters
     * 
     * @param endpoint
     *            The URL endpoint for the cloud storage service
     * @param provider
     *            A unique identifier identifying the type of storage API used to store this job's files - eg 'swift'
     * @param accessKey
     *            Username credential for accessing the storage service
     * @param secretKey
     *            Password credentials for accessing the storage service
     */
    public CloudStorageService(String endpoint, String provider, String accessKey, String secretKey) {
        this(endpoint, provider, accessKey, secretKey, null, false);
    }

    /**
     * Creates a new instance for connecting to the specified parameters
     * 
     * @param endpoint
     *            The URL endpoint for the cloud storage service
     * @param provider
     *            A unique identifier identifying the type of storage API used to store this job's files - eg 'swift'
     * @param accessKey
     *            Username credential for accessing the storage service
     * @param secretKey
     *            Password credentials for accessing the storage service
     * @param relaxHostName
     *            Whether security certs are required to strictly match the host
     */
    public CloudStorageService(String endpoint, String provider, String accessKey, String secretKey,
            boolean relaxHostName) {
        this(endpoint, provider, accessKey, secretKey, null, relaxHostName);
    }

    /**
     * Creates a new instance for connecting to the specified parameters
     * 
     * @param endpoint
     *            The URL endpoint for the cloud storage service
     * @param provider
     *            A unique identifier identifying the type of storage API used to store this job's files - eg 'swift'
     * @param accessKey
     *            Username credential for accessing the storage service
     * @param secretKey
     *            Password credentials for accessing the storage service
     * @param regionName
     *            The region identifier string for this service (if any). Can be null/empty.
     */
    public CloudStorageService(String endpoint, String provider, String accessKey, String secretKey, String regionName) {
        this(endpoint, provider, accessKey, secretKey, regionName, false);
    }

    /**
     * Creates a new instance for connecting to the specified parameters
     * 
     * @param endpoint
     *            The URL endpoint for the cloud storage service
     * @param provider
     *            A unique identifier identifying the type of storage API used to store this job's files - eg 'swift'
     * @param accessKey
     *            Username credential for accessing the storage service
     * @param secretKey
     *            Password credentials for accessing the storage service
     * @param regionName
     *            The region identifier string for this service (if any). Can be null/empty.
     * @param relaxHostName
     *            Whether security certs are required to strictly match the host
     */
    public CloudStorageService(String endpoint, String provider, String accessKey, String secretKey, String regionName,
            boolean relaxHostName) {
        this(endpoint, provider, accessKey, secretKey, regionName, relaxHostName, false);
    }

    /**
     * Creates a new instance for connecting to the specified parameters
     * 
     * @param endpoint
     *            The URL endpoint for the cloud storage service
     * @param provider
     *            A unique identifier identifying the type of storage API used to store this job's files - eg 'swift'
     * @param accessKey
     *            Username credential for accessing the storage service
     * @param secretKey
     *            Password credentials for accessing the storage service
     * @param regionName
     *            The region identifier string for this service (if any). Can be null/empty.
     * @param relaxHostName
     *            Whether security certs are required to strictly match the host
     * @param stripExpectHeader
     *            Whether to remove HTTP Expect header from requests; set to true for blobstores that do not support 100-Continue
     */
    public CloudStorageService(String endpoint, String provider, String accessKey, String secretKey, String regionName,
            boolean relaxHostName, boolean stripExpectHeader) {
        super();

        this.endpoint = endpoint;
        this.provider = provider;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.regionName = regionName;
        this.relaxHostName=relaxHostName;
        this.stripExpectHeader=stripExpectHeader;
        
        try {
            this.jobPrefix = "job-" + InetAddress.getLocalHost().getHostName() + "-";
        } catch (UnknownHostException e) {
            this.jobPrefix = "job-";
            log.error("Unable to lookup hostname. Defaulting prefix to " + this.jobPrefix, e);
        }
    }

    /*
     */
	public BlobStoreContext getBlobStoreContext(String arn, String clientSecret) {
    	if(mockBlobStoreContext!=null) return mockBlobStoreContext; // For unit test
    	
        Properties properties = new Properties();
        properties.setProperty("jclouds.relax-hostname", relaxHostName ? "true" : "false");
        properties.setProperty("jclouds.strip-expect-header", stripExpectHeader ? "true" : "false");

        if (regionName != null) {
            properties.setProperty("jclouds.region", regionName);
        }

        if(! TextUtil.isNullOrEmpty(arn)) {
            ContextBuilder builder = ContextBuilder.newBuilder("sts");
            if(accessKey!=null && secretKey!=null)
            	builder.credentials(accessKey, secretKey);
            
            STSApi api = builder.buildApi(STSApi.class);

            AssumeRoleOptions assumeRoleOptions = new AssumeRoleOptions().durationSeconds(3600).externalId(clientSecret);
            final UserAndSessionCredentials credentials = api.assumeRole(arn, "anvgl", assumeRoleOptions);

            Supplier<Credentials> credentialsSupplier = new Supplier<Credentials>() {
                @Override
                public Credentials get() {
                    return credentials.getCredentials();
                }
            };
            
            ContextBuilder builder2 = ContextBuilder.newBuilder("aws-s3").overrides(properties).credentialsSupplier(credentialsSupplier);
            
			if (this.endpoint != null) {
				builder2.endpoint(this.endpoint);
			}

            return builder2.buildView(BlobStoreContext.class);
        	
		} else {
			ContextBuilder builder = ContextBuilder.newBuilder(provider).overrides(properties);

			if (accessKey != null && secretKey != null)
				builder.credentials(accessKey, secretKey);

			if (this.endpoint != null) {
				builder.endpoint(this.endpoint);
			}

			return builder.build(BlobStoreContext.class);
		}
    }
    
	/**
     * Creates a new instance for connecting to the specified blob store. Please note that the connection credentials will NOT be available via this instances
     * get methods if this constructor is used.
     * 
     * @param blobStoreContext
     */
    public CloudStorageService(BlobStoreContext blobStoreContext) { // For unit test only!!
        this.mockBlobStoreContext = blobStoreContext;
    }

    /**
     * Username credential for accessing the storage service
     * 
     * @return
     */
    public String getAccessKey() {
        return accessKey;
    }

    /**
     * Password credential for accessing the storage service
     * 
     * @return
     */
    public String getSecretKey() {
        return secretKey;
    }

    /**
     * A unique identifier identifying the type of storage API used to store this job's files - eg 'swift'
     * 
     * @return
     */
    public String getProvider() {
        return provider;
    }

    /**
     * The URL endpoint for the cloud storage service
     * 
     * @return
     */
    public String getEndpoint() {
        return endpoint;
    }

    /**
     * Prefix to apply to any job files stored (will be appended with job id)
     * 
     * @return
     */
    public String getJobPrefix() {
        return jobPrefix;
    }

    /**
     * Prefix to apply to any job files stored (will be appended with job id)
     * 
     * @param jobPrefix
     */
    public void setJobPrefix(String jobPrefix) {
        this.jobPrefix = jobPrefix;
    }

    /**
     * The unique ID for this service - use it for distinguishing this service from other instances of this class - can be null or empty
     * 
     * @return
     */
    public String getId() {
        return id;
    }

    /**
     * The unique ID for this service - use it for distinguishing this service from other instances of this class - can be null or empty
     * 
     * @param id
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * The bucket where the data will be stored
     * 
     * @return
     * @throws PortalServiceException 
     */
    public String getBucket(String postFix) throws PortalServiceException {
    	if(TextUtil.isNullOrEmpty(postFix)) return bucketPrefix;
        try {
        	MessageDigest md = MessageDigest.getInstance("SHA-1");
        	
        	// Generate account specific bucket name. Replace characters which are not valid for AWS with arbitrary strings.
			String res = bucketPrefix+Base64.getEncoder().encodeToString(md.digest(postFix.getBytes("Utf-8"))).toLowerCase().replace('=', 'a').replace('/', 'b');
			
			// AWS restriction: Bucker name must be shorter than 64 characters:
			if(res.length()>63) return res= res.substring(0, 63); 
			
			return res;
		} catch (UnsupportedEncodingException | NoSuchAlgorithmException e) {
			throw new PortalServiceException("Could not create bucket name"+e.getMessage(), e);
		}
    }

    /**
     * The bucket where the data will be stored
     * 
     * @param bucket
     */
    public void setBucket(String bucket) {
        this.bucketPrefix = bucket;
    }

    /**
     * The authentication version to use when connecting to this object store - can be null or empty
     * 
     * @return
     */
    public String getAuthVersion() {
        return authVersion;
    }

    /**
     * The authentication version to use when connecting to this object store - can be null or empty
     * 
     * @param authVersion
     */
    public void setAuthVersion(String authVersion) {
        this.authVersion = authVersion;
    }

    /**
     * A short descriptive name for human identification of this service
     * 
     * @return
     */
    public String getName() {
        return name;
    }

    /**
     * A short descriptive name for human identification of this service
     * 
     * @param name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * The region identifier string for this service (if any). Can be null/empty. Currently this field is NON functional, it is only for descriptive purposes
     * due to limitations in JClouds.
     * 
     * @return
     */
    public String getRegionName() {
        return regionName;
    }

    /**
     * The region identifier string for this service (if any). Can be null/empty. Currently this field is NON functional, it is only for descriptive purposes
     * due to limitations in JClouds.
     * 
     * @param regionName
     */
    public void setRegionName(String regionName) {
        this.regionName = regionName;
    }

    /**
     * Utility for allowing only whitelisted characters
     * 
     * @param s
     * @return
     */
    private String sanitise(String s) {
        return s.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    /**
     * Utility for calculating an appropriate base cloud key for storing this jobs files
     * 
     * @param job
     * @return
     */
    public String generateBaseKey(CloudFileOwner job) {
        String baseKey = String.format("%1$s%2$s-%3$010d", jobPrefix, job.getUser(), job.getId());
        return sanitise(baseKey);
    }

    /**
     * Utility for generating the full path for a specific job file
     * 
     * @param job
     *            The job whose storage space will be queried for
     * @param key
     *            The key of the file (local to job).
     * @return
     */
    public String keyForJobFile(CloudFileOwner job, String key) {
        return String.format("%1$s/%2$s", jobToBaseKey(job), key);
    }

    /**
     * Gets the preconfigured base key for a job. If the job doesn't have a base key, one will be generated.
     * 
     * @param job
     *            Will have its baseKey parameter set if it's null
     * @return
     */
    protected String jobToBaseKey(CloudFileOwner job) {
        if (job.getStorageBaseKey() == null) {
            job.setStorageBaseKey(generateBaseKey(job));
        }

        return job.getStorageBaseKey();
    }

    /**
     * Utility to extract file size from a StorageMetadata interface
     * 
     * @param smd
     * @return
     */
    protected Long getFileSize(StorageMetadata smd) {
        if (smd instanceof BlobMetadataImpl) {
            ContentMetadata cmd = ((BlobMetadataImpl) smd).getContentMetadata();
            return cmd.getContentLength();
        } else if (smd instanceof MutableBlobMetadataImpl) {
            ContentMetadata cmd = ((MutableBlobMetadataImpl) smd).getContentMetadata();
            return cmd.getContentLength();
        } else {
            return 1L;
        }
    }

    /**
     * Gets the input stream for a job file identified by key.
     *
     * Ensure the resulting InputStream is closed
     *
     * @param job
     *            The job whose storage space will be queried
     * @param key
     *            The file name (no prefixes)
     * @return
     * @throws PortalServiceException
     */
    public InputStream getJobFile(CloudFileOwner job, String key, String arn, String clientSecret) throws PortalServiceException {
        try {
            BlobStore bs = getBlobStoreContext(arn, clientSecret).getBlobStore();
            Blob blob = bs.getBlob(getBucket(arn), keyForJobFile(job, key));
            return blob.getPayload().getInput();
        } catch (Exception ex) {
            log.error(String.format("Unable to get job file '%1$s' for job %2$s:", key, job));
            log.debug("error:", ex);
            throw new PortalServiceException("Error retriving output file details", ex);
        }
    }

    /**
     * Gets information about every file in the job's cloud storage space
     * 
     * @param job
     *            The job whose storage space will be queried
     * @return
     * @throws PortalServiceException
     */
    public CloudFileInformation[] listJobFiles(CloudFileOwner job, String arn, String clientSecret) throws PortalServiceException {

        try {
            BlobStore bs = getBlobStoreContext(arn, clientSecret).getBlobStore();
            String baseKey = generateBaseKey(job);

            String bucketName = getBucket(arn);
            
            //Paging is a little awkward - this list method may return an incomplete list requiring followup queries
            PageSet<? extends StorageMetadata> currentMetadataPage = bs.list(bucketName, ListContainerOptions.Builder.inDirectory(baseKey));
            String nextMarker = null;
            List<CloudFileInformation> jobFiles = new ArrayList<CloudFileInformation>();
            do {
                if (nextMarker != null) {
                    currentMetadataPage = bs.list(bucketName, ListContainerOptions.Builder
                            .inDirectory(baseKey)
                            .afterMarker(nextMarker));
                }

                //Turn our StorageMetadata objects into simpler CloudFileInformation objects
                for (StorageMetadata md : currentMetadataPage) {
                    long fileSize = 1L;
                    if (md instanceof BlobMetadataImpl) {
                        ContentMetadata cmd = ((BlobMetadataImpl) md).getContentMetadata();
                        fileSize = cmd.getContentLength();
                    } else if (md instanceof MutableBlobMetadataImpl) {
                        ContentMetadata cmd = ((MutableBlobMetadataImpl) md).getContentMetadata();
                        fileSize = cmd.getContentLength();
                    }
                    jobFiles.add(new CloudFileInformation(md.getName(), fileSize, md.getUri().toString()));
                }

                nextMarker = currentMetadataPage.getNextMarker();
            } while (nextMarker != null);

            return jobFiles.toArray(new CloudFileInformation[jobFiles.size()]);
        } catch (Exception ex) {
            log.error("Unable to list files for job:" + job.toString());
            log.debug("error:", ex);
            throw new PortalServiceException("Error retriving output file details", ex);
        }
    }

    /**
     * Uploads an array of local files into the specified job's storage space
     * 
     * @param job
     *            The job whose storage space will be used
     * @param files
     *            The local files to upload
     * @throws PortalServiceException
     */
    public void uploadJobFiles(CloudFileOwner job, File[] files, String arn, String clientSecret) throws PortalServiceException {

        try {
            BlobStore bs = getBlobStoreContext(arn, clientSecret).getBlobStore();

            String bucketName = getBucket(arn);
            bs.createContainerInLocation(null, bucketName);
            for (File file : files) {

                Blob newBlob = bs.blobBuilder(keyForJobFile(job, file.getName()))
                        .payload(file)
                        .build();

                bs.putBlob(bucketName, newBlob);

                log.debug(file.getName() + " uploaded to '" + bucketName + "' container");
            }
        } catch (AuthorizationException ex) {
            log.error("Storage credentials are not valid for job: " + job, ex);
            throw new PortalServiceException("Storage credentials are not valid.",
                    "Please provide valid storage credentials.");
        } catch (KeyNotFoundException ex) {
            log.error("Storage container does not exist for job: " + job, ex);
            throw new PortalServiceException("Storage container does not exist.",
                    "Please provide a valid storage container.");
        } catch (Exception ex) {
            log.error("Unable to upload files for job: " + job, ex);
            throw new PortalServiceException("An unexpected error has occurred while uploading file(s) to storage.",
                    "Please report it to cg-admin@csiro.au.");
        }
    }

    /**
     * Deletes all files including the container or directory for the specified job
     * 
     * @param job
     *            The whose storage space will be deleted
     * @throws PortalServiceException
     */
    public void deleteJobFiles(CloudFileOwner job, String arn, String clientSecret) throws PortalServiceException {
        try {
            BlobStore bs = getBlobStoreContext(arn, clientSecret).getBlobStore();
            bs.deleteDirectory(getBucket(arn), jobToBaseKey(job));
        } catch (Exception ex) {
            log.error("Error in removing job files or storage key.", ex);
            throw new PortalServiceException(
                    "An unexpected error has occurred while removing job files from S3 storage", ex);
        }
    }

	public void deleteJobFiles(CloudFileOwner job) throws PortalServiceException {
		deleteJobFiles(job, job.getProperty(CloudJob.PROPERTY_STS_ARN), job.getProperty(CloudJob.PROPERTY_CLIENT_SECRET));		
	}

	public void uploadJobFiles(CloudFileOwner job, File[] files) throws PortalServiceException {
		uploadJobFiles(job, files, job.getProperty(CloudJob.PROPERTY_STS_ARN), job.getProperty(CloudJob.PROPERTY_CLIENT_SECRET));
	}

	public CloudFileInformation[] listJobFiles(CloudFileOwner job) throws PortalServiceException {
		return listJobFiles(job, job.getProperty(CloudJob.PROPERTY_STS_ARN), job.getProperty(CloudJob.PROPERTY_CLIENT_SECRET));
	}

	public InputStream getJobFile(CloudFileOwner job, String myKey) throws PortalServiceException {
		return getJobFile(job, myKey, job.getProperty(CloudJob.PROPERTY_STS_ARN), job.getProperty(CloudJob.PROPERTY_CLIENT_SECRET));
	}
}