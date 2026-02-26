package no.nav.helsemelding.ediadapter.client

/**
 * Marks an API that exposes NHN EDI 2.0 v2-vNext functionality.
 *
 * These functions are considered experimental and may change or be removed without
 * prior notice as the upstream NHN API evolves.
 *
 * Any usage of a declaration annotated with `@ExperimentalEdiAdapterApi` must be
 * accepted either by annotating that usage with the [OptIn] annotation, e.g.
 * `@OptIn(ExperimentalEdiAdapterApi::class)`, or by using the compiler argument
 * `-opt-in=kotlin.time.ExperimentalEdiAdapterApi`.
 */
@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
annotation class ExperimentalEdiAdapterApi
