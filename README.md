# Flickr Fetcher

Demonstration project that fetches images from [Flickr
feed](https://www.flickr.com/services/feeds/docs/photos_public/) and save them
to disk.

## How to use this project

- build: `clj -X:build`
- run (the environment variables are compulsory):
  ```
  # Parallel workers count
  PARALLEL_COUNT=8 \
    # Directory to save images to (must exist)
    SAVE_DIR="/tmp/tmp.87QlzZoOj9" \
    # Time between 2 calls to the Flickr Feed URL to prevent harassing the server
    SLEEP_GRACE=100 \
    java -cp flickr-fetcher.jar clojure.main -m flickr-fetcher.core
  ```
- test it (the content type is compulsory, parameters are optional):
  ```
  curl -X POST \
    'http://localhost:8080/api/flickr' \
    -H 'Content-Type: application/json' \
    -d '{"count": 37, "height": 200, "width": 300}'
  ```
  Successful response is HTTP `204` _no content_.


## Where are tests?!

This project contains no tests because I chose to spend time working on
features instead. Production ready code I write obviously don't follow the same
rule. One can still notice the attention given in this repository in writing
pure functions as often as possible as well as splitting function into
functional blocks to ease testability.


## TODO

- HTTP errors are not really handled at all
- HTTP retries
- tests
- …
