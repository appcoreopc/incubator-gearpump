echo "Example: $0 <master ip> <master port> <spout> <bolt> <runseconds> <bytesPerMessage>"
ip=$1
port=$2
spout=$3
bolt=$4
runseconds=$5
bytespermsg=$6

echo java -cp "target/scala-2.10/gearpump-assembly-0.4-SNAPSHOT.jar" org.apache.gearpump.examples.sol.SOL  -ip $ip -port $port -spout $spout -bolt $bolt -runseconds $runseconds -bytesPerMessage $bytespermsg
java -cp "target/scala-2.10/gearpump-assembly-0.4-SNAPSHOT.jar" org.apache.gearpump.examples.sol.SOL  -ip $ip -port $port -spout $spout -bolt $bolt -runseconds $runseconds -bytesPerMessage $bytespermsg -stages 2