<script lang="ts">
    import moduleDocs from "./module-docs.json";

    interface Setting {
        name: string;
        type?: string;
        description: string;
    }

    interface ModuleDoc {
        name: string;
        category?: string;
        summary: string;
        useCases?: string[];
        settings: Setting[];
    }

    const modules: ModuleDoc[] = ((moduleDocs as { modules?: ModuleDoc[] }).modules ?? [])
        .slice()
        .sort((a, b) => a.name.localeCompare(b.name));

    const categories: string[] = [
        "All",
        ...Array.from(new Set(modules.map((m) => m.category ?? "Other"))).sort(),
    ];

    let search = $state("");
    let selectedCategory = $state("All");

    const filtered = $derived.by(() => {
        const query = search.trim().toLowerCase();
        return modules.filter((m) => {
            if (selectedCategory !== "All" && (m.category ?? "Other") !== selectedCategory) {
                return false;
            }
            if (!query) {
                return true;
            }
            return (
                m.name.toLowerCase().includes(query) ||
                (m.summary ?? "").toLowerCase().includes(query) ||
                m.settings.some((s) => s.name.toLowerCase().includes(query))
            );
        });
    });
</script>

<div class="docs">
    <div class="topbar">
        <div class="title">
            <h1>Module Documentation</h1>
            <span class="count">{modules.length} modules</span>
        </div>
        <input class="search" type="text" placeholder="Search modules or settings…" bind:value={search} />
        <span class="hint">Press ESC to close</span>
    </div>

    <div class="categories">
        {#each categories as cat (cat)}
            <button class="cat" class:active={selectedCategory === cat} on:click={() => (selectedCategory = cat)}>
                {cat}
            </button>
        {/each}
    </div>

    <div class="list">
        {#if modules.length === 0}
            <p class="empty">Documentation has not been generated yet.</p>
        {/if}

        {#each filtered as mod (mod.name)}
            <section class="module">
                <div class="module-head">
                    <span class="module-name">{mod.name}</span>
                    {#if mod.category}<span class="badge">{mod.category}</span>{/if}
                </div>

                <p class="summary">{mod.summary}</p>

                {#if mod.useCases && mod.useCases.length}
                    <div class="usecases">
                        <span class="label">Use cases</span>
                        <ul>
                            {#each mod.useCases as uc}
                                <li>{uc}</li>
                            {/each}
                        </ul>
                    </div>
                {/if}

                {#if mod.settings && mod.settings.length}
                    <table class="settings">
                        <thead>
                            <tr>
                                <th>Setting</th>
                                <th>Type</th>
                                <th>What it does</th>
                            </tr>
                        </thead>
                        <tbody>
                            {#each mod.settings as s (s.name)}
                                <tr>
                                    <td class="s-name">{s.name}</td>
                                    <td class="s-type">{s.type ?? ""}</td>
                                    <td class="s-desc">{s.description}</td>
                                </tr>
                            {/each}
                        </tbody>
                    </table>
                {:else}
                    <p class="no-settings">No configurable settings.</p>
                {/if}
            </section>
        {/each}

        {#if modules.length > 0 && filtered.length === 0}
            <p class="empty">No modules match your search.</p>
        {/if}
    </div>
</div>

<style lang="scss">
  .docs {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    padding: 24px 32px;
    box-sizing: border-box;
    color: var(--clickgui-text-color, #fff);
    font-family: "Inter", sans-serif;
    background-color: color-mix(in srgb, var(--surface-color, #1a1a1a) 92%, black);
  }

  .topbar {
    display: flex;
    align-items: center;
    gap: 16px;
    margin-bottom: 16px;

    .title {
      display: flex;
      align-items: baseline;
      gap: 10px;

      h1 {
        margin: 0;
        font-size: 22px;
        font-weight: 700;
      }

      .count {
        font-size: 12px;
        opacity: 0.6;
      }
    }

    .search {
      flex: 1;
      max-width: 420px;
      margin-left: auto;
      padding: 8px 12px;
      border-radius: 6px;
      border: 1px solid color-mix(in srgb, var(--clickgui-text-color, #fff) 18%, transparent);
      background-color: color-mix(in srgb, var(--surface-color, #1a1a1a) 70%, black);
      color: inherit;
      font-size: 13px;
      outline: none;

      &:focus {
        border-color: var(--accent-color, #4f8cff);
      }
    }

    .hint {
      font-size: 11px;
      opacity: 0.45;
    }
  }

  .categories {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-bottom: 14px;

    .cat {
      padding: 5px 12px;
      border: none;
      border-radius: 20px;
      cursor: pointer;
      font-size: 12px;
      color: inherit;
      background-color: color-mix(in srgb, var(--clickgui-text-color, #fff) 10%, transparent);
      transition: background-color 0.15s;

      &:hover {
        background-color: color-mix(in srgb, var(--clickgui-text-color, #fff) 18%, transparent);
      }

      &.active {
        background-color: var(--accent-color, #4f8cff);
        color: #fff;
      }
    }
  }

  .list {
    flex: 1;
    overflow-y: auto;
    padding-right: 8px;
  }

  .empty {
    opacity: 0.6;
    font-size: 14px;
    padding: 20px 0;
  }

  .module {
    background-color: color-mix(in srgb, var(--surface-color, #1a1a1a) 80%, black);
    border: 1px solid color-mix(in srgb, var(--clickgui-text-color, #fff) 10%, transparent);
    border-radius: 8px;
    padding: 16px 18px;
    margin-bottom: 14px;

    .module-head {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 6px;

      .module-name {
        font-size: 16px;
        font-weight: 700;
      }

      .badge {
        font-size: 10px;
        text-transform: uppercase;
        letter-spacing: 0.5px;
        padding: 2px 8px;
        border-radius: 10px;
        background-color: var(--accent-color, #4f8cff);
        color: #fff;
      }
    }

    .summary {
      margin: 0 0 10px;
      font-size: 13px;
      line-height: 1.5;
      opacity: 0.9;
    }

    .usecases {
      margin-bottom: 10px;

      .label {
        font-size: 11px;
        text-transform: uppercase;
        letter-spacing: 0.5px;
        opacity: 0.55;
      }

      ul {
        margin: 4px 0 0;
        padding-left: 18px;

        li {
          font-size: 12.5px;
          line-height: 1.5;
          opacity: 0.85;
        }
      }
    }

    .no-settings {
      margin: 0;
      font-size: 12px;
      opacity: 0.5;
      font-style: italic;
    }

    .settings {
      width: 100%;
      border-collapse: collapse;
      font-size: 12.5px;

      th {
        text-align: left;
        font-weight: 600;
        opacity: 0.55;
        padding: 6px 10px;
        border-bottom: 1px solid color-mix(in srgb, var(--clickgui-text-color, #fff) 12%, transparent);
        font-size: 11px;
        text-transform: uppercase;
        letter-spacing: 0.4px;
      }

      td {
        padding: 6px 10px;
        vertical-align: top;
        border-bottom: 1px solid color-mix(in srgb, var(--clickgui-text-color, #fff) 6%, transparent);
        line-height: 1.45;
      }

      .s-name {
        font-weight: 600;
        white-space: nowrap;
      }

      .s-type {
        opacity: 0.55;
        white-space: nowrap;
        font-family: monospace;
        font-size: 11.5px;
      }

      .s-desc {
        opacity: 0.9;
      }
    }
  }
</style>
