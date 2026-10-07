const source = "https://github.com/Gh0stDeveloper/Nexora-Git";

export function Footer() {
  return (
    <footer className="border-t border-[#21262d] bg-[#010409]">
      <div className="mx-auto flex max-w-7xl flex-col gap-5 px-4 py-10 text-sm text-[#8b949e] sm:px-6 lg:px-8">
        <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
          <div className="flex items-center gap-3">
            <img src="/nexora-git-icon.svg" alt="" className="h-7 w-7 rounded-md" />
            <span><strong className="text-[#c9d1d9]">Nexora Git</strong> · Open source under Apache-2.0</span>
          </div>
          <nav className="flex flex-wrap gap-x-5 gap-y-2" aria-label="Footer">
            <a className="hover:text-[#58a6ff]" href={source}>Source code</a>
            <a className="hover:text-[#58a6ff]" href={`${source}/blob/main/CONTRIBUTING.md`}>Contributing</a>
            <a className="hover:text-[#58a6ff]" href={`${source}/blob/main/SECURITY.md`}>Security</a>
            <a className="hover:text-[#58a6ff]" href="/privacy">Privacy</a>
            <a className="hover:text-[#58a6ff]" href="/verify">Verify build</a>
            <a className="hover:text-[#58a6ff]" href="/terms">Terms</a>
          </nav>
        </div>
        <p className="max-w-3xl leading-6">
          Nexora Git is an independent open-source project and is not affiliated with or endorsed by GitHub, Inc.
          GitHub is a trademark of GitHub, Inc.
        </p>
      </div>
    </footer>
  );
}
