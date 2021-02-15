# Apache Log DDOS Detection

# Install Filebeat
sudo yum install filebeat

# Run filebeat
sudo filebeat -e -c /opt/filebeat.yml

Note: For configuration check filebeat.yml

# Install Kafka 

Download kafka  https://kafka.apache.org/downloads and copy to /opt and unzip

# Start Zookeeper
sudo bin/zookeeper-server-start.sh config/zookeeper.properties

# Start Kafka Server
sudo bin/kafka-server-start.sh config/server.properties

# Create topic
sudo bin/kafka-topics.sh --create --topic log-events --bootstrap-server localhost:9092

# Describe
sudo bin/kafka-topics.sh --describe --topic log-events --bootstrap-server localhost:9092




