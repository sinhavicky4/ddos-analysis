
import io.netty.handler.codec.smtp.SmtpRequests.data
import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions.{col, count, from_json, split, to_timestamp, window}
import org.apache.spark.sql.streaming.OutputMode
import org.apache.spark.sql.types.{StringType, StructType}

object StreamLogAnalysis {

  def main(args: Array[String]): Unit = {
    Logger.getLogger("org").setLevel(Level.OFF)
    Logger.getLogger("akka").setLevel(Level.OFF)

    val spark = SparkSession
      .builder()
      .appName("DDOS Log Analysis").config("spark.sql.warehouse.dir", "warehouse/")
      .master("local[2]")
      .enableHiveSupport()
      .getOrCreate()

    import spark.implicits._

    val schema = new StructType()
      .add("@metadata", StringType, true)
      .add("@timestamp", StringType, true)
      .add("log", StringType, true)
      .add("message", StringType, true)
      .add("input", StringType, true)
      .add("esc", StringType, true)
      .add("host", StringType, true)
      .add("agent", StringType, true)

    val df = spark
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", "localhost:9092")
      .option("startingOffsets", "earliest")
      .option("enable.auto.commit", false)
      //.option("maxOffsetsPerTrigger", 50000)
      .option("subscribe", "log-events")
      .load().selectExpr("CAST(key AS STRING)", "CAST(value AS STRING) as json")
      .select(from_json(col("json"), schema).as("data")).where("data is not null")

    val kDf = df.withColumn("log", col("data").getField("message")).drop("data")

    val fDf = kDf.map(f => {
      val elements = f.getString(0).split(" ")
      (elements(0),
        //elements(1),elements(2),
        elements(3).replace("[", "") + " " + elements(4).replace("]", ""), //.replaceAll("[^a-zA-Z0-9:]", " ").trim(),
        //elements(4).replace("]",""),
        elements(5).replaceAll("[^a-zA-Z0-9]", ""),
        //elements(6),
        elements(7).replaceAll("[^a-zA-Z0-9/.]", ""),
        elements(8),
        elements(9)
      )
    }).toDF("ip", "logTime", "method", "protocol", "status", "resonseTime")

    val ts = to_timestamp($"logTime", "dd/MMM/yyyy:HH:mm:ss ZZZ")
    val finalLogDf = fDf.withColumn("logTimeStamp", ts).drop("logtime")

    var query = finalLogDf
     // .where("ip in ('130.235.188.52','168.156.41.239') ") //for test
      .withWatermark("logTimeStamp", "1 minutes") //use with append mode
      .groupBy( window($"logTimeStamp", "2 minutes", "1 minutes"),$"ip")
      .agg(count(col("ip")).as("count"))
      .where("count >500") //threshold of attack
      .writeStream
      //.format("console")
       .option("checkpointLocation", "chkpoint/")
      .outputMode(OutputMode.Update())
      //.outputMode(OutputMode.Complete())
      //.option("truncate", false)
      .foreachBatch{(batchDf:DataFrame,bacthId:Long)=>{
        batchDf.write.format("orc").mode("append").save("output/")
      }}
      .start()

    query.awaitTermination()


  }
}
