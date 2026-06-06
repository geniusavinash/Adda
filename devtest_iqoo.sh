echo "== /info =="
printf 'GET /info HTTP/1.0\r\n\r\n' | nc -w 5 127.0.0.1 8080 | tail -c 160
echo ""
ask() {
  B="$1"; L=${#B}
  printf "POST /ask HTTP/1.0\r\nContent-Type: application/json\r\nContent-Length: ${L}\r\n\r\n${B}" | nc -w 120 127.0.0.1 8080 | tail -c 420
  echo ""
}
echo "== T1: state a fact =="
ask '{"student":"Tester","question":"My favourite subject is Physics. Please remember this."}'
echo ""
echo "== T2: recall (multi-turn memory; expect Physics) =="
ask '{"student":"Tester","question":"Which subject did I say is my favourite?"}'
echo ""
echo "== T3: isolation (different student; should NOT know) =="
ask '{"student":"Other","question":"What is my favourite subject?"}'
echo "== DONE =="
