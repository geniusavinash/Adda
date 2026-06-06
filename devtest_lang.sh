ask() {
  S="$1"; Q="$2"
  B="{\"student\":\"$S\",\"question\":\"$Q\"}"
  L=${#B}
  echo "--- Q: $Q ---"
  printf "POST /ask HTTP/1.0\r\nContent-Type: application/json\r\nContent-Length: ${L}\r\n\r\n${B}" | nc -w 120 127.0.0.1 8080 | tail -c 500
  echo ""
}
ask "A" "Explain Newton third law in one line."
ask "B" "Photosynthesis kya hai? short mein batao."
ask "C" "What is the capital of France and why is it famous?"
ask "D" "Pythagoras theorem ko ek example ke saath samjhao."
echo "== DONE =="
