import com.gu.mediaservice.lib.aws.MessageSender
import com.gu.mediaservice.lib.elasticsearch.ElasticSearchConfig
import com.gu.mediaservice.lib.elasticsearch6.ElasticSearch6Config
import com.gu.mediaservice.lib.imaging.ImageOperations
import com.gu.mediaservice.lib.management.ManagementWithPermissions
import com.gu.mediaservice.lib.play.GridComponents
import controllers._
import lib._
import lib.elasticsearch.ElasticSearchVersion
import play.api.ApplicationLoader.Context
import router.Routes

class MediaApiComponents(context: Context) extends GridComponents(context) {
  final override lazy val config = new MediaApiConfig(configuration)

  val imageOperations = new ImageOperations(context.environment.rootPath.getAbsolutePath)

  val messageSender = new MessageSender(config, config.topicArn)
  val mediaApiMetrics = new MediaApiMetrics(config)

  val es1Config: ElasticSearchConfig = ElasticSearchConfig(
    alias = config.imagesAlias,
    host = config.elasticsearchHost,
    port = config.elasticsearchPort,
    cluster = config.elasticsearchCluster
  )

  val es6Config: ElasticSearch6Config = ElasticSearch6Config(
    alias = config.imagesAlias,
    host = config.elasticsearch6Host,
    port = config.elasticsearch6Port,
    cluster = config.elasticsearch6Cluster,
    shards = config.elasticsearch6Shards,
    replicas = config.elasticsearch6Replicas
  )

  val es1 = new lib.elasticsearch.impls.elasticsearch1.ElasticSearch(config, mediaApiMetrics, es1Config)
  val es6 = new lib.elasticsearch.impls.elasticsearch6.ElasticSearch(config, mediaApiMetrics, es6Config)

  val elasticSearch: ElasticSearchVersion = new lib.elasticsearch.TogglingElasticSearch(es1, es6)
  elasticSearch.ensureAliasAssigned()

  val s3Client = new S3Client(config)

  val usageQuota = new UsageQuota(config, elasticSearch, actorSystem.scheduler)
  usageQuota.scheduleUpdates()

  val imageResponse = new ImageResponse(config, s3Client, usageQuota)

  val mediaApi = new MediaApi(auth, messageSender, elasticSearch, imageResponse, config, controllerComponents, s3Client, mediaApiMetrics)
  val suggestionController = new SuggestionController(auth, elasticSearch, controllerComponents)
  val aggController = new AggregationController(auth, elasticSearch, controllerComponents)
  val usageController = new UsageController(auth, config, elasticSearch, usageQuota, controllerComponents)
  val healthcheckController = new ManagementWithPermissions(controllerComponents, mediaApi)

  override val router = new Routes(httpErrorHandler, mediaApi, suggestionController, aggController, usageController, healthcheckController)
}
