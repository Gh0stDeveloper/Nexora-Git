export default function NotFound() {
  return (
    <main className="mx-auto flex min-h-[60vh] max-w-3xl flex-col items-center justify-center px-4 py-16 text-center">
      <p className="mb-3 text-sm font-semibold text-[#58a6ff]">404</p>
      <h1 className="text-3xl font-bold">Page not found</h1>
      <p className="mt-3 text-[#8b949e]">The page you requested does not exist on the Nexora Git download site.</p>
      <a className="focus-ring mt-6 rounded-md bg-[#238636] px-4 py-2 text-sm font-semibold hover:bg-[#2ea043]" href="/">Return home</a>
    </main>
  );
}
