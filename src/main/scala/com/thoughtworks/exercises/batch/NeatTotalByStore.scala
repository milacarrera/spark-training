package com.thoughtworks.exercises.batch

import java.util.Properties

import org.apache.log4j.LogManager
import org.apache.spark.sql.SparkSession

object NeatTotalByStore {
  def main(args: Array[String]): Unit = {
    val log = LogManager.getLogger(this.getClass)

    val properties = new Properties()
    properties.load(this.getClass.getResourceAsStream(s"/application.properties"))
    val baseBucket = properties.getProperty("base_bucket")
    val username = properties.get("username")
    val dataFilesBucket = properties.getProperty("data_files_bucket")

    val ordersBucket = s"$baseBucket/$username/$dataFilesBucket/orders"
    val orderItemsBucket = s"$baseBucket/$username/$dataFilesBucket/orderItems"
    val productsBucket = s"$baseBucket/$username/$dataFilesBucket/products"

    val spark = SparkSession
      .builder()
//      .master("local")
      .appName("Data Engineering Capability Development - ETL Exercises")
      .getOrCreate()

    val dfOrdersRaw = spark.read
      .option("delimiter", ";")
      .option("header", true)
      .option("infer_schema", true)
      .csv(ordersBucket)

    val dfOrderItemsRaw = spark.read
      .option("delimiter", ";")
      .option("header", true)
      .option("infer_schema", true)
      .csv(orderItemsBucket)

    val dfProductsRaw = spark.read
      .option("delimiter", ";")
      .option("header", true)
      .option("infer_schema", true)
      .csv(productsBucket)

    import org.apache.spark.sql.functions._
    import spark.implicits._

    val dfOrdersWithItems = dfOrdersRaw
      .join(dfOrderItemsRaw, "OrderId")
      .as("ooi")
      .join(dfProductsRaw.as("p"), col("ooi.ProductId") === col("p.ProductId"))

    val totals = dfOrdersWithItems
      .groupBy($"ooi.StoreId")
      .agg(sum(($"p.Price" - $"ooi.Discount") * $"ooi.Quantity" ).as("total"))
      .select($"StoreID", $"total")
      .collect()
      .map(x => (x.getAs[String](0), x.getAs[Double](1)))

    val locale = new java.util.Locale("pt", "BR")
    val formatter = java.text.NumberFormat.getCurrencyInstance(locale)
    val totalsFormatted = totals.map(x => (x._1, formatter.format(x._2)))

    totalsFormatted.foreach(x => log.info(s"O total de vendas da loja ${x._1} foi de ${x._2}"))
    totalsFormatted.foreach(x => println(s"O total de vendas da loja ${x._1} foi de ${x._2}"))
    //O total de vendas da loja c35c8e0c-8e0b-47cc-8d5a-9fb0cb8799bb foi R$ 46.451.700.278,54
    //O total de vendas da loja 6e80e53d-6c9a-455f-b324-0ca9fda7e6f7 foi R$ 46.429.891.592,29
    //O total de vendas da loja b25df442-c2f4-4f4b-8973-f16d214a55a6 foi R$ 46.398.468.098,30
    //O total de vendas da loja 05936c42-a9ad-4541-bae5-5aa406c0e180 foi R$ 46.389.990.776,29

  }
}
