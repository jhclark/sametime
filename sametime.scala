import java.io.PrintWriter
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent._

class FutureString(val input: String) {
  private var _output: String = null
  def set(output: String) {
    this.synchronized {
      _output = output
      //Console.err.println("Setting " + output + " and notifying all waiters")
      this.notifyAll
    }
  }
  // blocks until string is ready
  def get: String = {
    while(_output == null) {
      this.synchronized {
        //Console.err.println("Waiting on " + input)
        this.wait
      }
    }
    return _output
  }
}

object ExceptionHandler extends Thread.UncaughtExceptionHandler {
  override def uncaughtException(t: Thread, e: Throwable) {
    e.printStackTrace
    // terminate the entire program if any thread dies
    sys.exit(1)
  }
}

object SameTime extends App {
  Console.err.println("SameTime")
  Console.err.println("By Jonathan Clark")

  def usage() {
    Console.err.println("Usage: sametime 'child command'")
    sys.exit(1)
  }

  if(args.size < 1) usage

  val opts = args.toList.dropRight(1)
  val childCmd = args.toList.last

  val cores = Runtime.getRuntime.availableProcessors
  Console.err.println("Using %d cores".format(cores))
  Console.err.println("Running command: %s".format(childCmd))

  implicit def runnable(f: => Unit): Runnable = new Runnable { override def run = f }
  implicit def callable[T](f: => T): Callable[T] = new Callable[T] { override def call = f }

  // Stdin/stdout mode
  // Use a producer/consumer queue while keeping the lines in same order...
  val qSize = 100000

  val inputs = new ArrayBlockingQueue[FutureString](qSize)
  val outputs = new ArrayBlockingQueue[FutureString](qSize)
  val POISON = new FutureString(null)

  def spawnThread(func: => Unit) = {
    val thread = new Thread(func)
    thread.setUncaughtExceptionHandler(ExceptionHandler)
    thread.start
    thread
  }
  
  def reader {
    try {
      var line = Console.in.readLine
      while(line != null) {
        // blocks when bounded queue is full
        val future = new FutureString(line)
        //Console.err.println("Put: " + line)
        inputs.put(future)
        outputs.put(future)

        line = Console.in.readLine
      }
    } finally {
      Console.in.close
    }
    (0 until cores).foreach(_ => inputs.put(POISON))
    //Console.err.println("Joining reader")
  }

  def writer {
    var future: FutureString = outputs.take // blocks when empty
    while(future.input != null) { // POISON
      println(future.get)
      future = outputs.take
    }
    //Console.err.println("Joining writer")
  }

  def worker {
    // start the worker process    
    // TODO: Work dir?
    val proc = Runtime.getRuntime.exec(childCmd)
    val localQ = new ArrayBlockingQueue[FutureString](qSize)

    def stdinHandler {
      val stdin = new PrintWriter(proc.getOutputStream)

      var future: FutureString = inputs.take // blocks when empty
      while(future.input != null) { // POISON
        stdin.println(future.input)
        localQ.put(future)

        future = inputs.take
      }
      localQ.put(POISON)
      stdin.close
    }
    
    def stdoutHandler {
      val stdout = new BufferedReader(new InputStreamReader(proc.getInputStream))

      var future: FutureString = localQ.take
      while(future.input != null) { // POISON
        val result = stdout.readLine
        future.set(result)
        future = localQ.take
      }
      stdout.close
    }

    def stderrHandler {
      val stderr = new BufferedReader(new InputStreamReader(proc.getErrorStream))
      var line = stderr.readLine
      while(line != null) {
        Console.err.println(line)
        line = stderr.readLine
      }
      stderr.close
    }

    val stdinThread = spawnThread(stdinHandler)
    val stdoutThread = spawnThread(stdoutHandler)
    val stderrThread = spawnThread(stderrHandler)
    val code = proc.waitFor
    stdinThread.join
    stdoutThread.join
    stderrThread.join

    if(code != 0) {
      throw new RuntimeException("Process returned non-zero: %s".format(childCmd))
    }
  }

  val readerThread = spawnThread(reader)
  val workers: Seq[Thread] = (0 until cores).map(_ => spawnThread(worker)).toSeq
  val writerThread = spawnThread(writer)

  readerThread.join
  workers.foreach(_.join)
  outputs.put(POISON)
  writerThread.join
}
