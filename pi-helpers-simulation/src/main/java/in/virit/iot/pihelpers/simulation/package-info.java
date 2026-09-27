/**
 * Simulated hardware for the pi-helpers services: CDI alternatives with priority, so they
 * replace the real services just by being on the classpath. Each implements
 * {@link in.virit.iot.pihelpers.Simulated}, which makes the panels show that their data is
 * made up. Keep this artifact out of production builds, e.g. in a Maven profile for
 * development and in test scope.
 */
package in.virit.iot.pihelpers.simulation;
