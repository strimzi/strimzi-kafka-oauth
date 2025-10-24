
wait_for_url() {
    URL=$1
    MSG=$2

    if [[ $URL == https* ]]; then
        CMD="curl -k -sL -o /dev/null -w %{http_code} $URL"
    else
        CMD="curl -sL -o /dev/null -w %{http_code} $URL"
    fi

    # Run 'curl' but suppress any error code from 'set -e' error hook
    status=$($CMD || echo "000")
    until [[ "$status" == "200" || "$status" == "405" ]]
    do
        echo "$MSG ($URL)"
        status=$($CMD || echo "000")
        sleep 2
    done
}
