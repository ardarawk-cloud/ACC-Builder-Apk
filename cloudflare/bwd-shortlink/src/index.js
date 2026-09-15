// Public short alias for Bali Wedding DJ booking.
// Keep this Worker intentionally stateless: no auth, no DB, no secrets.
export default {
  async fetch(request) {
    const incoming = new URL(request.url);
    const target = new URL('https://bali-wedding-dj-booking.ardarawk.workers.dev');
    target.pathname = incoming.pathname;
    target.search = incoming.search;
    return Response.redirect(target.toString(), 302);
  }
};
