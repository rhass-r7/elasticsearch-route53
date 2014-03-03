elasticsearch-route53
=====================

This is an Elasticsearch plugin which registers the cluster in Route53 after reading hosted zone info from the ES properties.

# Shortcut to installing Route53Plugin

On Elasticsearch 0.90:

TODO change README accordingly
    
    $ bin/plugin -url https://s3-eu-west-1.amazonaws.com/downloads.9apps.net/elasticsearch-Route53Plugin-0.90.0.zip -install Route53Plugin

On Elasticsearch 1.0:

    $ bin/plugin -url https://s3-eu-west-1.amazonaws.com/downloads.9apps.net/elasticsearch-Route53Plugin-1.0.1.zip -install Route53Plugin


# Generating the installable plugin

To generate the plugin for installation you need to use maven:

    $ mvn clean package

which generates a file such as target/release/elasticsearch-Route53Plugin-0.90.0.zip.

# Installing the plugin

Install it from the Elasticsearch installation directory, by running (change the location of your file):

    $ bin/plugin -url file:/home/flavia/elasticsearch-Route53Plugin-0.90.0.zip -install Route53Plugin

To uninstall it you can run:

    $ bin/plugin -remove Route53Plugin

# Configuring the plugin

The plugin has some options that you can configure in the elasticsearch.yml:

  * route53.registration.enabled: To enable or disable the plugin itself. True by default.
  * route53.aws.access_key and route53.aws.secret_key (or access_key_id and secret_access_key): AWS credentials of the account where the hosted zone is registered. No default values. If using IAM, it should have at least permission to change record sets in Route53.
  * route53.hosted_zone and route53.hosted_zone_id: The hosted zone name and id where you want to register your cluster. The name assigned will be <cluster name>.<hosted zone name>.

# Development

We use Eclipse with the m2 plugin for development.
