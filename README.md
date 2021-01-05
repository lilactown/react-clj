# react-clj

This repo holds the rationale, design and (perhaps, eventually) an
implementation of select parts of [ReactJS'](https://reactjs.org/) API in pure
Clojure.

## Why?

ReactJS is primarily used as a client-side framework for building rich user
interfaces on the web. Other use cases include building UIs on mobile, desktop,
as well as statically generating HTML on the server. Typically these other use
cases presuppose you are running including a JS environment in order to use the
ReactJS' runtime and code written for it.

This library is targeted at those other use cases by building a runtime that
reflects a large enough part of ReactJS' API to allow the building of apps that
run in both JS and Clojure environments.

This can enable a number of things in Clojure that are difficult today depending
on the specific React wrapper library one chooses:
* Building web applications which server-render the application before hydrating
  client-side.
* Taking advantage of far-in-the-future planned features like ReactJS "server
  components," by rendering components to a wire protocol on the server and
  streaming the result to the client.
* Building cross-platform applications that share UI definitions, e.g. an app
  that runs on the web and in a desktop (swing, javafx) or terminal environment
  via the JVM.

### Aren't there already a bunch of libraries that implement SSR on the JVM?

There are probably a half a dozen ReactJS wrappers that support SSR on the JVM.
The trouble with them so far is that:
* They are typically specialized to rendering to HTML
* They often bundle their own additional runtime and features (e.g. using 
  hiccup)

This makes them useful if you're building a web app with these wrappers and
happen to want to render some HTML on the JVM, but are less useful for other
use cases.

The design of this library is meant to be much smaller and more general. Just
like in JS how you can have multiple "reconcilers" which take ReactJS components
and render them on whatever platform you want to target, the goal of this
library is to be just as flexible.

## Non-goals

* Be the fastest way to generate HTML on the server
* Build a reconciler or whatever for JavaFX or any other platform
* Replace ReactJS in the browser

## Design

### Rendering elements

ReactJS' core API is actually fairly small. We first introduce one fundamental
data type and a single protocol:

```clojure
(defrecord Element [type props key])

(defprotocol IRender
  (-render [c props] "Render a component, returning a tree of Elements"))
```

This is essentially our public API for defining components and creating elements
out of components and "native" types (e.g. DOM nodes).

Next there are a few special element "types" that we are going to define up
front:

```clojure
(def fragment 'react/fragment)

(def provider 'react/provider)

(def suspense 'react/suspense)
```

Others may be added later, but this is enough for us to start constructing
trees of elements for a basic UI:

```clojure
(extend-type clojure.lang.Fn
  IRender
  (-render [f props] (f props)))

(defn my-component
  [{:keys [user-name]}]
  (->Element
   fragment
   {:children ["Hello, " user-name "!"]}
   nil))

(-render my-component {:user-name "Shirley"})
;; => {:type 'react/fragment, :props {:children ["Hello, " "Shirley" "!"]}, :key nil}
```

### Reconciliation

Links:
* https://reactjs.org/docs/reconciliation.html
* https://github.com/acdlite/react-fiber-architecture
* https://github.com/facebook/react/tree/master/packages/react-reconciler

We will probably want to create a runtime similar to React Fiber which consumers
can then build custom reconcilers on top of. This probably(?) isn't necessary
for SSG or server components, but may(?) be desirable for things like rendering
a dynamic UI. Who knows?

Good news: we already have rich tools for doing immutable updates and
concurrency-safe containers.

Bad news: we live in a multithreaded world on the JVM, which requires additional
thought to see if ReactJS' model works well outside of a single threaded
context.

### Hooks

Implementation TBD based on reconciliation.
