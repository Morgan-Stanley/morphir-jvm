package morphir.flowz.spark
import org.apache.spark.sql.{ Dataset, Encoder, SparkSession }

import scala.reflect.ClassTag
import scala.reflect.runtime.universe.TypeTag
import zio._

trait FlowzSparkModule { sparkFlowz: morphir.flowz.Api =>

  type sparkModule = morphir.flowz.spark.sparkModule.type
  val sparkModule: sparkModule = morphir.flowz.spark.sparkModule

  import sparkModule.SparkModule

  type SparkFlow[-StateIn, +StateOut, -Env, -Input, +Err, +Output] =
    Flow[StateIn, StateOut, Env with SparkModule, Input, Err, Output]

  type SparkStep[-Env, -In, +Err, +Out] = Step[Env with SparkModule, In, Err, Out]
  type SparkTaskStep[-In, +Out]         = Step[SparkModule, In, Throwable, Out]

  object SparkFlow extends FlowCompanion with SparkFlowCompanion {
    def mapDataset[A, B <: Product: ClassTag: TypeTag](func: A => B): TaskStep[Dataset[A], Dataset[B]] =
      Flow.task { dataset: Dataset[A] =>
        import dataset.sparkSession.implicits._
        dataset.map(func)
      }

    def transformDataset[A, B <: Product: ClassTag: TypeTag, S1, S2](
      func: (S1, Dataset[A]) => (S2, Dataset[B])
    ): Flow[S1, S2, Any, Dataset[A], Throwable, Dataset[B]] =
      Flow.statefulEffect(func)
  }

  object SparkStep extends FlowCompanion with SparkFlowCompanion {
    def mapDataset[A, B <: Product: ClassTag: TypeTag](func: A => B): TaskStep[Dataset[A], Dataset[B]] =
      Flow.task { dataset: Dataset[A] =>
        import dataset.sparkSession.implicits._
        dataset.map(func)
      }

    def transformDataset[A, B <: Product: ClassTag: TypeTag, S1, S2](
      func: (S1, Dataset[A]) => (S2, Dataset[B])
    ): Flow[S1, S2, Any, Dataset[A], Throwable, Dataset[B]] =
      Flow.statefulEffect(func)

    def apply[Env, Params, Out](
      func: Params => RIO[Env with SparkModule, Out]
    ): SparkStep[Env, Params, Throwable, Out] =
      Flow.context[Env with SparkModule, Any, Params].flatMap { ctx =>
        Flow(func(ctx.inputs.params).provide(ctx.environment).map(out => OutputChannels.fromValue(out)))
      }

  }

  trait SparkFlowCompanion { self: FlowCompanion =>

    def createDataset[A <: Product: ClassTag: TypeTag](
      func: SparkSession => Encoder[A] => Dataset[A]
    ): SparkStep[Any, Any, Throwable, Dataset[A]] =
      Flow(
        ZIO
          .environment[FlowContext.having.Environment[SparkModule]]
          .mapEffect { ctx =>
            val spark = ctx.environment.get.sparkSession
            FlowValue.fromValue(func(spark)(spark.implicits.newProductEncoder))
          }
      )

    def createDataset[A <: Product: ClassTag: TypeTag](
      data: => Seq[A]
    ): SparkStep[Any, Any, Throwable, Dataset[A]] =
      Flow(
        ZIO
          .environment[FlowContext.having.Environment[SparkModule]]
          .mapEffect { ctx =>
            val spark = ctx.environment.get.sparkSession
            FlowValue.fromValue(spark.createDataset(data)(spark.implicits.newProductEncoder))
          }
      )

    def environment[Env]: SparkStep[Env, Any, Nothing, Env with SparkModule] =
      Flow(
        ZIO
          .environment[FlowContext.having.Environment[Env with SparkModule]]
          .map(ctx => FlowValue.fromValue(ctx.environment))
      )

    /**
     * A step that returns the given parameters.
     */
    def parameters[In]: SparkStep[Any, In, Throwable, In] =
      Flow.context[SparkModule, Any, In].transformEff((_, ctx) => (ctx.inputs.params, ctx.inputs.params))

    def makeStep[Env, Params, Err, Out](
      func: Params => ZIO[Env with SparkModule, Err, Out]
    ): SparkStep[Env, Params, Err, Out] =
      Flow.parameters[Params].flatMap { params =>
        Flow.fromEffect(func(params))
      }

    def showDataset[A](): SparkStep[Any, Dataset[A], Throwable, Dataset[A]] =
      parameters[Dataset[A]].tapValue { dataset =>
        ZIO.effect(dataset.show())
      }

    def showDataset[A](truncate: Boolean): SparkStep[Any, Dataset[A], Throwable, Dataset[A]] =
      parameters[Dataset[A]].tapValue { dataset =>
        ZIO.effect(dataset.show(truncate))
      }

    def showDataset[A](numRows: Int, truncate: Boolean): SparkStep[Any, Dataset[A], Throwable, Dataset[A]] =
      parameters[Dataset[A]].tapValue { dataset =>
        ZIO.effect(dataset.show(numRows, truncate))
      }

    def showDataset[A](numRows: Int, truncate: Int): SparkStep[Any, Dataset[A], Throwable, Dataset[A]] =
      parameters[Dataset[A]].tapValue { dataset =>
        ZIO.effect(dataset.show(numRows, truncate))
      }

    def withSpark[A](func: SparkSession => A): SparkStep[Any, Any, Throwable, A] =
      Flow(
        ZIO
          .environment[FlowContext.having.Environment[SparkModule]]
          .mapEffect(ctx => FlowValue.fromValue(func(ctx.environment.get.sparkSession)))
      )

    def withSparkEffect[Env, Err, A](func: SparkSession => ZIO[Env, Err, A]): SparkStep[Env, Any, Err, A] =
      Flow(
        ZIO
          .environment[FlowContext.having.Environment[Env with SparkModule]]
          .flatMap(ctx =>
            func(ctx.environment.get.sparkSession).map(OutputChannels.unified(_)).provide(ctx.environment)
          )
      )
  }

}
