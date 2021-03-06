/**
 * Run simulations for the sparse noisy period estimation problem.
 * Various estimators are available.
 */
import pubsim.poly.CircularNoisePolynomialPhaseSignal
import pubsim.poly.KitchenEstimator
import pubsim.poly.DPTEstimator
import pubsim.poly.MbestEstimator
import pubsim.poly.BabaiEstimator
import pubsim.poly.MbestEstimator
import pubsim.poly.MaximumLikelihood
import pubsim.poly.SphereDecoderEstimator
import pubsim.distributions.ContinuousRandomVariable
import pubsim.poly.PolynomialPhaseEstimator
import pubsim.poly.bounds.AngularLeastSquaresVariance
import pubsim.distributions.circular.WrappedUniform

val iters = 2000 //number of Monte-Carlo trials.
val Ns = List(10,50,200) //values of N we will generate curves for
val ms = List(2,3) //order of our polynomial phase signals

def gp( a : Double, r : Double, n : Int ) : Double = a * scala.math.pow(r,n)

//returns an array of noise distributions with a logarithmic scale
def noises = (0 to 25).map( n => gp(0.002,1.15,n) ).map( v => new WrappedUniform(0,v) )

//Returns a list of functions that return estimators we will run (factory patern to enable parallelism)
def estfactory(m : Int, N : Int) : List[() => PolynomialPhaseEstimator] = {
  var ret = List( 
    () => new KitchenEstimator(m,N),
    () => new DPTEstimator(m,N),
    () => new BabaiEstimator(m,N),
    () => new MbestEstimator(m,N,4*N) 
  )
  //add the sphere decoder and Least squares estimators if N and m are small
  if( N < 60 ) ret = ret :+ ( () => new SphereDecoderEstimator(m,N) )
  if( N < 60 && m <= 2 ) ret = ret :+ ( () => new MaximumLikelihood(m,N) )
  return ret
}

val starttime = (new java.util.Date).getTime

//for all the the values of N and m.
for( N <-  Ns; m <- ms ) {

  for(estf <- estfactory(m,N) ){
    
    val estname = estf().getClass.getSimpleName + "N" + N.toString + "m" + m + "WU"
    print("Running " + estname)
    val eststarttime = (new java.util.Date).getTime

    //for all the noise distributions (in parallel threads)
    val mselist = noises.par.map { noise =>

      val siggen =  new CircularNoisePolynomialPhaseSignal(N) //construct a signal generator
      siggen.setNoiseGenerator(noise)
      val est = estf() //construct an estimator
				  
      var mse = new Array[Double](m+1) //storage for the mses
      for( itr <- 1 to iters ) {
	siggen.generateRandomParameters(m)
	val p0 = siggen.getParameters
	siggen.generateReceivedSignal
	val err = est.error(siggen.getReal, siggen.getImag, p0)
	for( i <- mse.indices ) mse(i) += err(i) 
      }
      print(".")
      mse //last thing is what gets returned 
    }.toList
    
    //now write all the data to a file
    val files = (0 to m).map( v => new java.io.FileWriter(estname + "p" + v) ) //list of files to write to
    (mselist, noises).zipped.foreach{ (mse, noise) =>
      for ( i <- files.indices ) 
	files(i).write(noise.unwrappedVariance.toString.replace('E', 'e') + "\t" + (mse(i)/iters).toString.replace('E', 'e')  + "\n") 
    }
    for( f <- files ) f.close //close all the files we wrote to 

    val estruntime = (new java.util.Date).getTime - eststarttime
    println(" finished in " + (estruntime/1000.0) + " seconds.")
  }

  //Least squares unwrapping asymptotics
  {
    val lsuasymp = new AngularLeastSquaresVariance(N,m)
    val files = (0 to m).map( v => new java.io.FileWriter("LSUasympWUN" + N + "m" + m  + "p" + v) )
    for ( i <- files.indices ) {
      for(noise <- noises ) {
	files(i).write(noise.unwrappedVariance.toString.replace('E', 'e') + "\t" + lsuasymp.getBound(i,noise).toString.replace('E', 'e') + "\n")
      }
    }
    for ( f <- files ) f.close //close all the files we wrote to 
  }

}

val runtime = (new java.util.Date).getTime - starttime
  println("Simulation finshed in " + (runtime/1000.0) + " seconds.\n")
