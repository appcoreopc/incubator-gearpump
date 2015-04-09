/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.gearpump.experiments.storm.processor

import java.util.concurrent.TimeUnit

import akka.actor.Cancellable
import backtype.storm.generated.Bolt
import backtype.storm.task.{IBolt, OutputCollector}
import backtype.storm.tuple.TupleImpl
import backtype.storm.utils.Utils
import org.apache.gearpump.Message
import org.apache.gearpump.cluster.UserConfig
import org.apache.gearpump.experiments.storm.util.{GraphBuilder, StormTuple}
import org.apache.gearpump.streaming.task.{StartTime, Task, TaskContext}
import scala.collection.JavaConversions._

import scala.concurrent.duration.FiniteDuration

private[storm] class StormProcessor (taskContext : TaskContext, conf: UserConfig)
  extends Task(taskContext, conf) {
  import org.apache.gearpump.experiments.storm.util.StormUtil._

  private val topology = getTopology(conf)
  private val stormConfig = getStormConfig(conf)
  private val pid = taskContext.taskId.processorId
  private val boltId = conf.getString(GraphBuilder.COMPONENT_ID).getOrElse(
    throw new RuntimeException(s"Storm bolt id not found for processor $pid"))
  private val boltSpec = conf.getValue[Bolt](GraphBuilder.COMPONENT_SPEC).getOrElse(
    throw new RuntimeException(s"Storm bolt spec not found for processor $pid"))
  private val bolt = Utils.getSetComponentObject(boltSpec.get_bolt_object()).asInstanceOf[IBolt]

  private var count = 0
  private var snapShotTime : Long = System.currentTimeMillis()
  private var snapShotWordCount : Long = 0

  private var scheduler : Cancellable = null

  override def onStart(startTime: StartTime): Unit = {
    val delegate = new StormBoltOutputCollector(pid, boltId, taskContext)
    val topologyContext = getTopologyContext(topology, stormConfig,
      Map(pid -> boltId), pid)
    bolt.prepare(stormConfig, topologyContext, new OutputCollector(delegate))
    scheduler = taskContext.schedule(new FiniteDuration(5, TimeUnit.SECONDS),
      new FiniteDuration(5, TimeUnit.SECONDS))(reportWordCount)
  }

  override def onNext(msg: Message): Unit = {
    val stormTuple = msg.msg.asInstanceOf[StormTuple]
    val values = stormTuple.values
    val sourceTaskId = stormTuple.sourceTaskId
    val sourceComponentId = stormTuple.sourceComponentId
    val streamId = stormTuple.streamId

    val topologyContext = getTopologyContext(topology, stormConfig,
      Map(pid -> boltId, sourceTaskId -> sourceComponentId), pid)

    val tuple = new TupleImpl(topologyContext, values, sourceTaskId, streamId, null)
    bolt.execute(tuple)
    count += 1
  }

  private def reportWordCount() : Unit = {
    val current : Long = System.currentTimeMillis()
    LOG.info(s"Task ${taskContext.taskId} Throughput: ${(count - snapShotWordCount, (current - snapShotTime) / 1000)} (words, second)")
    snapShotWordCount = count
    snapShotTime = current
  }

}