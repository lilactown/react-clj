# Reconciliation

ReactJS' reconciler package does two things:
1. Hosts the internal runtime they use schedule work
2. Provides a public interface for extending React to be used by new host envs,
   e.g. iOS or desktop

This document will explore both of those.

## The internal runtime

The truly novel thing that ReactJS does is what they call "time slicing," where
they split up work across frames of execution to allow things like:
* awaiting asynchronous operations to complete before rendering
* interrupting low-priority work to allow high-priority work to run before
  resuming

The way that ReactJS does this is through their "fiber" architecture, where
they essentially implement their own call graph in user land, reifying them as
JS objects so that they may use cooperative concurrency algorithms to handle
the execution of your ReactJS application.

This makes some sense in a world where you can't do shared-memory concurrency
(e.g. most JavaScript environments). The first question we should ask is: does
this make sense on the JVM, where shared-memory concurrency is the status quo?

If we were only interested in the prioritization and performance of taking the
tree of elements, diffing and creating host nodes, then we could reasonably
start out using a synchronous reconciler, or one that schedules work on a thread
pool, and then try building a fancier reconciler later and compare.

However, I do believe that to allow the UX of a component "suspending" while it
does some IO, we do need mechanism to:
1. Detect that the tree we are rendering is "suspending"
2. Commit a partial(?) tree with a placeholder at the point where we suspended
3. Finish rendering the tree with the IO complete
4. Commit the final tree

If we assume that the rendering takes place on a separate thread, and the IO may
block until completion, then that still leaves the question of how we
communicate to the UI thread that it should commit the tree with a placeholder,
and later commit the finished tree. It is complex enough that we cannot naively
call `(render tree changes)` and wait for it to return.

### Fibers

[Project Loom](https://github.com/openjdk/loom) is an implementation of
delimited continuations on the JVM, aka fibers in the nomenclature - they
dropped that terminology when they started focusing on the higher level "virtual
thread" abstraction but kept the underlying idea.

Virtual threads are something that a reconciler that uses a thread pool could
obviously immediately benefit from, as it would allow thousands of isolated
updates to be calculated at once with no changes. It essentially does the work
to implement the cooperative concurrency that ReactJS has had to handle
themselves. We would have to build prioritization on top of thread pools, but
this is fairly well understood on the JVM AFAICT.

Another option is that we could take advantage of Loom's lower level
"Continuation" class. Essentially we would standardize on Loom as our 
reconciliation runtime and attempt to replicate ReactJS' fiber architecture and
algorithms.

Some downsides to this:
* This depends on Loom's Continuation class being publicly available, which they
  did promise at the start of the project and have backtracked to say that it
  _may_ be available after the initial release.
* This depends on Loom's Continuations being cloneable or multishot. They did
  did say at the start of the project that they would be cloneable but have been
  coy about whether it's still on the roadmap and it is not currently
  implemented.
* Would have to implement our own cooperative concurrency algorithms ourselves,
  in a shared memory world.

For these reasons it seems incredibly risky to depend directly on Project Loom
right now. It would be less risky to build on top of a thread pool and, in the
future, swap in a virtual thread pool to finally achieve Webscale(tm).
