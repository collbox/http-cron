# http-cron

POST to HTTP endpoints at regular intervals (to trigger jobs, etc.).
Can be run as a standalone daemon, reading from a `cron.yaml` file, or
as a [component][] in a Clojure system.

Also a drop-in replacement for the process which runs on AWS Elastic
Beanstalk worker instances in the presence of a `cron.yaml`, providing
an alternative which can be run in a local environment.

# Running

`http-cron` can run as a standalone CLI application, or as a component
in a Clojure / Java application.

## Running as a Standalone Service (CLI)

Clojure (`clojure`) needs to be installed and available on the `PATH`.

Run the application by invoking the `bin/run` script.

In standalone mode, tasks are configured with a `cron.yaml` file.  By
default, `http-cron` will look for a file with this name in the
project root.

Additional settings can be controlled with command-line arguments, or
with environment variables, making the program convenient to run
directly or via a containerization solution like [Docker][].

| Env. Var.            | CLI Argument | Default                 | Description                                                 |
|----------------------|--------------|-------------------------|-------------------------------------------------------------|
| `HTTP_CRON_JOB_FILE` | `-f`         | `cron.yaml`             | File name for cron.yaml-style job specification             |
| `HTTP_CRON_BASE_URI` | `-b`         | `http://localhost:8080` | Base URL to POST to                                         |
| `HTTP_CRON_HOST`     |              | `localhost`             | Host to POST to (used if `HTTP_CRON_BASE_URI` not provided) |
| `HTTP_CRON_PORT`     |              | `8080`                  | Port to POST to (used if `HTTP_CRON_BASE_URI` not provided) |

## Running as a Component in an Application

`http-cron` is built using (Stuart Sierra's) [component][], making it
easy to run it as part of a larger Clojure system.  The easiest way to
build the component is using the
`collbox.http-cron.core/make-http-cron` function, passing values for
`:base-uri` and `:job-specs` in the configuration map.

Here's an example of usage which reads the cron jobs from an AWS-style
`cron.yaml` file, ignoring some jobs (based on their name):

```clj
(ns myapp.system
  (:require [collbox.http-cron.conversion :as hc.conv]
            [collbox.http-cron.core :as hc]
            [com.stuartsierra.component :as component]))

(defn app-system []
  (component/map->SystemMap
   {:cron-http (hc/make-http-cron
                {:base-uri  "http://localhost:8080"
                 :job-specs (->> (hc.conv/parse-cron-yaml "cron.yaml")
                                 (remove (comp #{"backup"
                                                 "data-lake-export"}
                                               :name)))})}))
```

If you prefer to specify the jobs directly in code (rather than
loading from a `cron.yaml`) you can simply provide a sequence of maps
with `:name`, `:url`, and `:schedule` entries.

n.b. the value of `:schedule` should be a cron specifier string in
[the format used by Quartz][quartz-cron-expressions], which differs
slightly from the format supported in the `cron.yaml` file.

# Cron Job Configuration

If you're using a `cron.yaml` file, it should be in the following
format:

```
version: 1
cron:
  - name: "brew-the-coffee"
    url: "/jobs/coffee"
    schedule: "0 7 * * 1-5"
  - name: "make-magic"
    url: "/jobs/magic"
    schedule: "0/15 * * * *"
```

The fields of the cron specification are:

`minute hour day-of-month month day-of-week`

You can also provide a full URL for `url`, rather than a URL fragment,
and `http-cron` will use that URL, ignoring the value of
`HTTP_CRON_BASE_URI`.

# AWS Elastic Beanstalk Worker Compatibility

`http-cron` is designed to be a drop-in replacement for the similar
service which runs on Elastic Beanstalk worker instances.  Towards
that end, it:

- Supports the same `cron.yaml` file format (or as much of it as I can
  suss out, given the lack of a specification).
- Provides `x-aws-sqsd-path` and `x-aws-sqsd-taskname` HTTP headers.

The AWS `cron.yaml` format differs somewhat from the capabilities
provided by some other services like Google App Engine, though this
project could likely be extended to support other popular formats--PRs
are welcome.

# License

Copyright © 2022 Collbox Inc.

Distributed under the MIT License.

[component]: https://github.com/stuartsierra/component
[docker]: https://www.docker.com
[quartz-cron-expressions]: http://www.quartz-scheduler.org/documentation/quartz-2.3.0/tutorials/crontrigger.html
