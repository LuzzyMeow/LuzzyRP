// RP-Hub interface components: selectors, layout and reusable views.

// --- Custom select ---
(function () {
    const { ref, computed, nextTick, watch, onBeforeUnmount } = Vue;

    const toOption = (option, index) => {
        if (!option || typeof option !== 'object') {
            return {
                value: option,
                label: String(option ?? ''),
                description: '',
                disabled: false,
                group: '',
                key: `${index}:${String(option ?? '')}`
            };
        }

        const value = option.value;
        return {
            value,
            label: option.label ?? String(value ?? ''),
            description: option.description || '',
            disabled: !!option.disabled,
            group: option.group || '',
            key: option.key ?? `${index}:${String(value ?? '')}`
        };
    };

    window.RPHubCustomSelect = {
        name: 'CustomSelect',
        props: {
            modelValue: {
                type: [String, Number, Boolean],
                default: ''
            },
            options: {
                type: Array,
                default: () => []
            },
            placeholder: {
                type: String,
                default: '请选择'
            },
            disabled: {
                type: Boolean,
                default: false
            },
            buttonClass: {
                type: [String, Array, Object],
                default: ''
            },
            menuClass: {
                type: [String, Array, Object],
                default: ''
            },
            optionClass: {
                type: [String, Array, Object],
                default: ''
            }
        },
        emits: ['update:modelValue', 'change'],
        setup(props, { emit }) {
            const isOpen = ref(false);
            const triggerRef = ref(null);
            const menuRef = ref(null);
            const menuStyle = ref({});
            let listenersActive = false;

            const normalizedOptions = computed(() => props.options.map(toOption));
            const optionMatches = (left, right) => (
                Object.is(left, right)
                || (left !== undefined && right !== undefined && String(left) === String(right))
            );
            const selectedOption = computed(() => (
                normalizedOptions.value.find(option => optionMatches(option.value, props.modelValue))
            ));
            const selectedLabel = computed(() => selectedOption.value?.label || props.placeholder);

            const shouldShowGroup = (index) => {
                const option = normalizedOptions.value[index];
                if (!option?.group) return false;
                return index === 0 || normalizedOptions.value[index - 1]?.group !== option.group;
            };

            const updateMenuPosition = () => {
                const trigger = triggerRef.value;
                if (!trigger) return;

                const rect = trigger.getBoundingClientRect();
                const viewportWidth = window.innerWidth || document.documentElement.clientWidth || rect.width;
                const viewportHeight = window.innerHeight || document.documentElement.clientHeight || 600;
                const margin = 8;
                const gap = 6;
                const belowSpace = viewportHeight - rect.bottom - margin;
                const aboveSpace = rect.top - margin;
                const openAbove = belowSpace < 180 && aboveSpace > belowSpace;
                const width = Math.max(160, rect.width);
                const left = Math.min(Math.max(margin, rect.left), Math.max(margin, viewportWidth - width - margin));
                const maxHeight = Math.max(120, Math.min(320, (openAbove ? aboveSpace : belowSpace) - gap));

                menuStyle.value = {
                    position: 'fixed',
                    left: `${left}px`,
                    width: `${width}px`,
                    maxHeight: `${maxHeight}px`,
                    zIndex: 10000,
                    ...(openAbove
                        ? { bottom: `${viewportHeight - rect.top + gap}px` }
                        : { top: `${rect.bottom + gap}px` })
                };
            };

            const closeMenu = () => {
                isOpen.value = false;
            };

            const openMenu = async () => {
                if (props.disabled) return;
                isOpen.value = true;
                await nextTick();
                updateMenuPosition();
            };

            const toggleMenu = () => {
                if (isOpen.value) {
                    closeMenu();
                    return;
                }
                openMenu();
            };

            const selectOption = (option) => {
                if (!option || option.disabled) return;
                emit('update:modelValue', option.value);
                emit('change', option.value);
                closeMenu();
            };

            const isSelected = (option) => optionMatches(option.value, props.modelValue);

            const onDocumentPointerDown = (event) => {
                const trigger = triggerRef.value;
                const menu = menuRef.value;
                const target = event.target;
                if (trigger?.contains(target) || menu?.contains(target)) return;
                closeMenu();
            };

            const onKeyDown = (event) => {
                if (event.key === 'Escape') closeMenu();
            };

            const addOpenListeners = () => {
                if (listenersActive) return;
                document.addEventListener('pointerdown', onDocumentPointerDown, true);
                document.addEventListener('keydown', onKeyDown);
                window.addEventListener('resize', updateMenuPosition);
                window.addEventListener('scroll', updateMenuPosition, true);
                listenersActive = true;
            };

            const removeOpenListeners = () => {
                if (!listenersActive) return;
                document.removeEventListener('pointerdown', onDocumentPointerDown, true);
                document.removeEventListener('keydown', onKeyDown);
                window.removeEventListener('resize', updateMenuPosition);
                window.removeEventListener('scroll', updateMenuPosition, true);
                listenersActive = false;
            };

            watch(isOpen, async (open) => {
                if (open) {
                    await nextTick();
                    updateMenuPosition();
                    addOpenListeners();
                } else {
                    removeOpenListeners();
                }
            });

            watch(() => props.options, () => {
                if (isOpen.value) nextTick(updateMenuPosition);
            }, { deep: true });

            onBeforeUnmount(removeOpenListeners);

            return {
                isOpen,
                triggerRef,
                menuRef,
                menuStyle,
                normalizedOptions,
                selectedLabel,
                shouldShowGroup,
                toggleMenu,
                selectOption,
                isSelected
            };
        },
        template: `
            <div class="relative w-full">
                <button
                    ref="triggerRef"
                    type="button"
                    :disabled="disabled"
                    :aria-expanded="isOpen ? 'true' : 'false'"
                    aria-haspopup="listbox"
                    :class="[
                        'relative flex w-full items-center justify-between gap-2 rounded-xl border border-gray-200 bg-white px-4 py-2.5 text-left text-sm font-medium text-gray-800 shadow-sm transition-all hover:border-gray-300 focus:border-primary-500 focus:outline-none focus:ring-2 focus:ring-primary-500/20 disabled:cursor-not-allowed disabled:opacity-60',
                        buttonClass
                    ]"
                    @click="toggleMenu"
                >
                    <span class="truncate">{{ selectedLabel }}</span>
                    <svg
                        :class="['h-4 w-4 shrink-0 text-gray-400 transition-transform duration-200', isOpen ? 'rotate-180 text-gray-600' : '']"
                        fill="none"
                        stroke="currentColor"
                        viewBox="0 0 24 24"
                    >
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.4" d="M19 9l-7 7-7-7"></path>
                    </svg>
                </button>

                <teleport to="body">
                    <transition
                        enter-active-class="transition duration-150 ease-out"
                        enter-from-class="opacity-0 -translate-y-1 scale-[0.98]"
                        enter-to-class="opacity-100 translate-y-0 scale-100"
                        leave-active-class="transition duration-100 ease-in"
                        leave-from-class="opacity-100 translate-y-0 scale-100"
                        leave-to-class="opacity-0 -translate-y-1 scale-[0.98]"
                    >
                        <div
                            v-if="isOpen"
                            ref="menuRef"
                            :style="menuStyle"
                            :class="[
                                'overflow-y-auto rounded-xl border border-gray-200 bg-white p-1.5 shadow-2xl shadow-gray-900/15 backdrop-blur-xl custom-scrollbar',
                                menuClass
                            ]"
                            role="listbox"
                        >
                            <div v-if="normalizedOptions.length === 0" class="px-3 py-2 text-sm text-gray-400">
                                暂无选项
                            </div>
                            <template v-for="(option, index) in normalizedOptions" :key="option.key">
                                <div
                                    v-if="shouldShowGroup(index)"
                                    class="px-2.5 pb-1 pt-2 text-[10px] font-bold uppercase tracking-wider text-gray-400 first:pt-1"
                                >
                                    {{ option.group }}
                                </div>
                                <button
                                    type="button"
                                    role="option"
                                    :aria-selected="isSelected(option) ? 'true' : 'false'"
                                    :disabled="option.disabled"
                                    :class="[
                                        'flex w-full items-center justify-between gap-2 rounded-lg px-3 py-2 text-left text-sm transition-colors',
                                        option.disabled ? 'cursor-not-allowed text-gray-300' : 'text-gray-700 hover:bg-primary-50 hover:text-primary-700',
                                        isSelected(option) && !option.disabled ? 'bg-primary-50 text-primary-700 font-bold' : '',
                                        optionClass
                                    ]"
                                    @click="selectOption(option)"
                                >
                                    <span class="min-w-0">
                                        <span class="block truncate">{{ option.label }}</span>
                                        <span v-if="option.description" class="mt-0.5 block truncate text-[11px] font-normal text-gray-400">
                                            {{ option.description }}
                                        </span>
                                    </span>
                                    <svg
                                        v-if="isSelected(option)"
                                        class="h-4 w-4 shrink-0 text-primary-600"
                                        fill="none"
                                        stroke="currentColor"
                                        viewBox="0 0 24 24"
                                    >
                                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.6" d="M5 13l4 4L19 7"></path>
                                    </svg>
                                </button>
                            </template>
                        </div>
                    </transition>
                </teleport>
            </div>
        `
    };
})();

// --- Application layout ---
(function () {
    const primaryItems = Object.freeze([
        { view: 'chat', label: '聊天', icon: 'M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z' },
        { view: 'usage', label: '用量统计', icon: 'M4 19V9m5 10V5m5 14v-7m5 7V3M3 21h18' },
        { view: 'memory', label: '记忆系统', status: 'memory', icon: 'M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z' },
        { view: 'uitemplates', label: 'UI模板', status: 'ui', icon: 'M4 5a2 2 0 012-2h12a2 2 0 012 2v14a2 2 0 01-2 2H6a2 2 0 01-2-2V5zm4 3h8M8 12h8M8 16h5' },
        { view: 'characters', label: '角色卡管理', icon: 'M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z' }
    ]);
    const onlineItems = Object.freeze([
        { view: 'generator', label: '角色卡生成', icon: 'M15 8a3 3 0 11-6 0 3 3 0 016 0zm-3 5c-4 0-7 2-7 5v1h8m5-6v6m-3-3h6' },
        { view: 'novel', label: '小说生成', icon: 'M20 19V16H7C5.34315 16 4 17.3431 4 19M8.8 22H16.8C17.9201 22 18.4802 22 18.908 21.782C19.2843 21.5903 19.5903 21.2843 19.782 20.908C20 20.4802 20 19.9201 20 18.8V5.2C20 4.07989 20 3.51984 19.782 3.09202C19.5903 2.71569 19.2843 2.40973 18.908 2.21799C18.4802 2 17.9201 2 16.8 2H8.8C7.11984 2 6.27976 2 5.63803 2.32698C5.07354 2.6146 4.6146 3.07354 4.32698 3.63803C4 4.27976 4 5.11984 4 6.8V17.2C4 18.8802 4 19.7202 4.32698 20.362C4.6146 20.9265 5.07354 21.3854 5.63803 21.673C6.27976 22 7.11984 22 8.8 22Z' },
        { view: 'square', label: '万相广场', square: true }
    ]);
    const advancedItems = Object.freeze([
        { view: 'presets', label: '预设', icon: 'M12 6V4m0 2a2 2 0 100 4m0-4a2 2 0 110 4M6 18a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4m6 6v10m6-2a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4' },
        { view: 'worldinfo', label: '世界书', icon: 'M12 6.25v13m0-13C10.83 5.48 9.25 5 7.5 5S4.17 5.48 3 6.25v13C4.17 18.48 5.75 18 7.5 18s3.33.48 4.5 1.25m0-13C13.17 5.48 14.75 5 16.5 5S19.83 5.48 21 6.25v13C19.83 18.48 18.25 18 16.5 18s-3.33.48-4.5 1.25' },
        { view: 'regex', label: '正则', title: '正则脚本', icon: 'M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4' },
        { view: 'tools', label: '工具', icon: 'M14.7 6.3a1 1 0 000 1.4l1.6 1.6a1 1 0 001.4 0l3.77-3.77a6 6 0 01-7.94 7.94l-6.91 6.91a2.12 2.12 0 01-3-3l6.91-6.91a6 6 0 017.94-7.94L14.7 6.3z' }
    ]);

    const AppSidebar = {
        props: {
            currentView: { type: String, required: true },
            collapsed: Boolean,
            onlineOpen: Boolean,
            advancedOpen: Boolean,
            memoryProcessing: Boolean,
            uiTemplateRunning: Boolean,
            user: { type: Object, required: true }
        },
        emits: ['update:current-view', 'update:collapsed', 'toggle-online', 'toggle-advanced', 'close-mobile'],
        setup(props, { emit }) {
            window.RPHubUpdateCheck.useUpdateCheck();
            const selectView = (view) => {
                emit('update:current-view', view);
                emit('close-mobile');
            };
            const itemClass = (view) => view === props.currentView
                ? 'bg-primary-50 text-primary-700'
                : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900';
            return {
                advancedItems,
                advancedViews: advancedItems.map(item => item.view),
                itemClass,
                onlineItems,
                onlineViews: onlineItems.map(item => item.view),
                primaryItems,
                selectView
            };
        },
        template: `
            <div @click="$emit('close-mobile')"
                class="fixed inset-0 bg-black/30 backdrop-blur-sm z-40 md:hidden mobile-overlay"></div>

            <div class="app-sidebar fixed inset-y-0 left-0 z-50 w-72 md:w-72 bg-white/95 border-r border-gray-200/80 transform transition-all duration-300 md:relative md:translate-x-0 flex flex-col shadow-2xl md:shadow-sm md:rounded-none rounded-r-3xl overflow-hidden"
                :class="collapsed ? 'md:w-16' : 'md:w-72'">
                <div class="h-16 flex items-center border-b border-gray-100/80 bg-white/70 backdrop-blur-xl transition-all duration-300"
                    :class="collapsed ? 'justify-center px-0' : 'justify-between px-6'">
                    <div v-show="!collapsed" class="app-logo relative inline-flex items-baseline gap-1.5 pr-1 min-w-0">
                        <span class="text-[21px] font-extrabold text-gray-800 tracking-[0.08em] leading-none">RP</span>
                        <span class="text-[16px] font-semibold text-primary-600 tracking-[0.18em] leading-none">HUB</span>
                        <span class="absolute -bottom-1 left-0 h-[2px] w-11 rounded-full bg-primary-500/60"></span>
                    </div>
                    <button @click="$emit('update:collapsed', !collapsed)"
                        :class="['hidden md:flex items-center justify-center bg-white hover:bg-gray-50 border border-gray-200/80 rounded-xl text-gray-500 hover:text-primary-600 transition-all shadow-sm active:scale-95', collapsed ? 'w-12 h-12 p-0' : 'p-2']"
                        :title="collapsed ? '展开侧边栏' : '收起侧边栏'">
                        <svg v-if="!collapsed" class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 19l-7-7 7-7M20 12H4"></path>
                        </svg>
                        <svg v-else class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 5l7 7-7 7M4 12h16"></path>
                        </svg>
                    </button>
                </div>

                <div class="sidebar-nav custom-scrollbar flex-1 overflow-y-auto py-4 transition-all duration-300"
                    :class="collapsed ? 'px-2 space-y-2' : 'px-3 space-y-1.5'">
                    <button v-for="item in primaryItems" :key="item.view" @click="selectView(item.view)" :title="item.label"
                        :class="['sidebar-nav-button flex items-center rounded-xl transition-all duration-200 font-medium', itemClass(item.view), collapsed ? 'w-12 h-12 mx-auto justify-center p-0' : 'w-full px-3 py-2.5']">
                        <svg class="w-5 h-5" :class="collapsed ? 'mr-0' : 'mr-3'" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" :d="item.icon"></path>
                        </svg>
                        <span v-show="!collapsed" class="whitespace-nowrap overflow-hidden">{{ item.label }}</span>
                        <span v-if="!collapsed && ((item.status === 'memory' && memoryProcessing) || (item.status === 'ui' && uiTemplateRunning))" class="ml-auto">
                            <span class="inline-block w-2 h-2 rounded-full bg-primary-500 memory-extracting"></span>
                        </span>
                    </button>

                    <div class="advanced-nav" :class="{ 'is-open': onlineOpen && !collapsed }">
                        <button @click="$emit('toggle-online')"
                            class="sidebar-nav-button advanced-nav-trigger flex items-center rounded-xl transition-all duration-200 font-medium"
                            :class="[onlineViews.includes(currentView) ? 'bg-primary-50 text-primary-700' : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900', collapsed ? 'w-12 h-12 mx-auto justify-center p-0' : 'w-full px-3 py-2.5']"
                            title="在线" aria-controls="online-nav-panel" :aria-expanded="onlineOpen && !collapsed">
                            <svg class="w-5 h-5" :class="collapsed ? 'mr-0' : 'mr-3'" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 12a9 9 0 01-9 9m9-9a9 9 0 00-9-9m9 9H3m9 9a9 9 0 01-9-9m9 9c1.657 0 3-4.03 3-9s-1.343-9-3-9m0 18c-1.657 0-3-4.03-3-9s1.343-9 3-9m-9 9a9 9 0 019-9"></path>
                            </svg>
                            <span v-show="!collapsed" class="whitespace-nowrap overflow-hidden">在线</span>
                            <svg v-show="!collapsed" class="advanced-nav-chevron ml-auto w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"></path>
                            </svg>
                        </button>
                        <div id="online-nav-panel" class="advanced-nav-panel" :aria-hidden="!(onlineOpen && !collapsed)" :inert="!(onlineOpen && !collapsed)">
                            <div class="advanced-nav-panel-inner"><div class="advanced-nav-list">
                                <button v-for="item in onlineItems" :key="item.view" @click="selectView(item.view)"
                                    class="sidebar-nav-button advanced-nav-item transition-all duration-200" :class="itemClass(item.view)" :title="item.label">
                                    <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                                        <template v-if="item.square">
                                            <rect x="3" y="3" width="7" height="7" rx="2" stroke-width="1.8"></rect>
                                            <rect x="14" y="3" width="7" height="7" rx="2" stroke-width="1.8"></rect>
                                            <rect x="3" y="14" width="7" height="7" rx="2" stroke-width="1.8"></rect>
                                            <rect x="14" y="14" width="7" height="7" rx="2" stroke-width="1.8"></rect>
                                        </template>
                                        <path v-else stroke-linecap="round" stroke-linejoin="round" :stroke-width="item.view === 'generator' ? 1.8 : 2" :d="item.icon"></path>
                                    </svg>
                                    <span>{{ item.label }}</span>
                                </button>
                            </div></div>
                        </div>
                    </div>

                    <div class="advanced-nav" :class="{ 'is-open': advancedOpen && !collapsed }">
                        <button @click="$emit('toggle-advanced')"
                            class="sidebar-nav-button advanced-nav-trigger flex items-center rounded-xl transition-all duration-200 font-medium"
                            :class="[advancedViews.includes(currentView) ? 'bg-primary-50 text-primary-700' : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900', collapsed ? 'w-12 h-12 mx-auto justify-center p-0' : 'w-full px-3 py-2.5']"
                            title="高级" aria-controls="advanced-nav-panel" :aria-expanded="advancedOpen && !collapsed">
                            <svg class="w-5 h-5" :class="collapsed ? 'mr-0' : 'mr-3'" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 7.5h16M7.5 3v9m9 0v9M4 16.5h16"></path>
                                <circle cx="7.5" cy="16.5" r="2" fill="white" stroke="currentColor" stroke-width="2"></circle>
                                <circle cx="16.5" cy="7.5" r="2" fill="white" stroke="currentColor" stroke-width="2"></circle>
                            </svg>
                            <span v-show="!collapsed" class="whitespace-nowrap overflow-hidden">高级</span>
                            <svg v-show="!collapsed" class="advanced-nav-chevron ml-auto w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"></path>
                            </svg>
                        </button>
                        <div id="advanced-nav-panel" class="advanced-nav-panel" :aria-hidden="!(advancedOpen && !collapsed)" :inert="!(advancedOpen && !collapsed)">
                            <div class="advanced-nav-panel-inner"><div class="advanced-nav-list">
                                <button v-for="item in advancedItems" :key="item.view" @click="selectView(item.view)"
                                    class="sidebar-nav-button advanced-nav-item transition-all duration-200" :class="itemClass(item.view)" :title="item.title || item.label">
                                    <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8" :d="item.icon"></path>
                                    </svg>
                                    <span>{{ item.label }}</span>
                                </button>
                            </div></div>
                        </div>
                    </div>

                    <button @click="selectView('settings')" title="设置"
                        :class="['sidebar-nav-button flex items-center rounded-xl transition-all duration-200 font-medium', itemClass('settings'), collapsed ? 'w-12 h-12 mx-auto justify-center p-0' : 'w-full px-3 py-2.5']">
                        <svg class="w-5 h-5" :class="collapsed ? 'mr-0' : 'mr-3'" fill="none" stroke="currentColor" viewBox="0 0 24 24"><use href="#icon-settings"></use></svg>
                        <span v-show="!collapsed" class="whitespace-nowrap overflow-hidden">设置</span>
                    </button>
                </div>

                <div class="p-3 border-t border-gray-100/80 bg-white/70 backdrop-blur-xl">
                    <div class="flex items-center transition-all" :class="collapsed ? 'justify-center' : 'rounded-2xl border border-gray-200/70 bg-gray-50/80 px-3 py-2 shadow-sm'">
                        <div class="w-10 h-10 rounded-2xl overflow-hidden shadow-sm flex-shrink-0 ring-2 ring-white">
                            <img v-if="user?.avatar" :src="user.avatar" class="w-full h-full object-cover">
                            <div v-else class="w-full h-full bg-gradient-to-br from-primary-500 to-primary-600 flex items-center justify-center text-white font-bold">
                                {{ user.name.charAt(0).toUpperCase() }}
                            </div>
                        </div>
                        <div v-if="!collapsed" class="ml-3 whitespace-nowrap overflow-hidden">
                            <div class="text-sm font-bold text-gray-900 truncate">{{ user.name }}</div>
                            <div class="text-xs text-gray-500">User</div>
                        </div>
                    </div>
                </div>
            </div>`
    };

    window.RPHubLayoutComponents = Object.freeze({ AppSidebar });
})();

// --- Reusable views and modals ---
(function () {
    const { onBeforeUnmount, ref } = Vue;
    const CustomSelect = window.RPHubCustomSelect;

    const UiTemplatePending = {
        template: `
            <div class="ui-template-pending-card" role="status" aria-live="polite">
                <div class="ui-template-pending-icon" aria-hidden="true">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                            d="M4 5a2 2 0 012-2h12a2 2 0 012 2v14a2 2 0 01-2 2H6a2 2 0 01-2-2V5zm4 3h8M8 12h8M8 16h5">
                        </path>
                    </svg>
                    <span class="live-dots"><i></i><i></i><i></i></span>
                </div>
                <div class="ui-template-pending-content">
                    <div class="ui-template-pending-row">
                        <span class="ui-template-pending-title">分析中</span>
                    </div>
                </div>
            </div>`
    };

    const EmbeddedViewContent = {
        props: {
            src: String,
            loading: Boolean,
            loadingText: String
        },
        emits: ['load', 'menu'],
        template: `
            <button @click="$emit('menu')"
                class="md:hidden absolute left-0 top-1/2 transform -translate-y-1/2 z-20 pl-2 pr-1.5 py-3 bg-white/90 backdrop-blur-md text-gray-600 text-xs font-medium rounded-r-xl shadow-lg border border-l-0 border-gray-200 active:scale-95 transition-all flex flex-col items-center gap-1">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 19l-7-7m0 0l7-7m-7 7h18"></path>
                </svg>
                <span class="leading-none">返</span>
                <span class="leading-none">回</span>
            </button>
            <div class="flex-1 w-full relative bg-white h-full">
                <div v-if="loading" class="absolute inset-0 z-10 flex items-center justify-center bg-gray-50">
                    <div class="flex flex-col items-center">
                        <svg class="embedded-loading-spinner" viewBox="0 0 50 50" fill="none" aria-hidden="true">
                            <circle cx="25" cy="25" r="20" stroke-width="3"></circle>
                        </svg>
                        <div class="text-gray-500 font-medium">{{ loadingText }}</div>
                    </div>
                </div>
                <iframe :src="src" @load="$emit('load')" class="absolute inset-0 w-full h-full border-0"
                    allow="clipboard-write"
                    sandbox="allow-scripts allow-same-origin allow-forms allow-popups allow-modals allow-downloads"></iframe>
            </div>`
    };

    const GenerationTimer = {
        props: {
            waitTime: Number,
            estimatedTime: Number,
            remoteEstimatedTime: Number,
            remote: Boolean
        },
        template: `
            <div class="flex items-center gap-1.5 text-[11px] text-gray-500 font-mono bg-white/50 backdrop-blur-sm px-2.5 py-1 rounded-full border border-white/50 animate-fade-in mt-1 shadow-sm typing-timer-badge">
                <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                </svg>
                <span class="whitespace-nowrap">
                    {{ waitTime }}s
                    <span v-if="estimatedTime || remoteEstimatedTime" class="text-gray-300 mx-0.5">/</span>
                    <span v-if="estimatedTime && !remote">{{ estimatedTime }}s</span>
                    <span v-else-if="remoteEstimatedTime">{{ remoteEstimatedTime }}s</span>
                </span>
            </div>`
    };

    const SettingsPageHeader = {
        props: { title: String },
        emits: ['menu'],
        template: `
            <div class="settings-page-header">
                <div class="flex items-center">
                    <button @click="$emit('menu')" class="mobile-menu-button">
                        <svg class="w-6 h-6" fill="none" stroke="currentColor"><use href="#icon-menu"></use></svg>
                    </button>
                    <h2 class="text-xl md:text-2xl font-bold text-gray-800 flex items-center">
                        <slot name="icon"></slot>
                        {{ title }}
                        <slot name="title-extra"></slot>
                    </h2>
                </div>
                <div v-if="$slots.default" class="flex space-x-2 md:space-x-3">
                    <slot></slot>
                </div>
            </div>`
    };

    const SettingsHelp = {
        props: {
            topic: { type: String, required: true },
            openTopic: { type: String, default: '' },
            label: { type: String, required: true },
            triggerClass: { type: String, default: '' },
            popoverClass: { type: String, default: '' },
            iconClass: { type: String, default: 'h-3.5 w-3.5' }
        },
        emits: ['toggle'],
        template: `
            <button type="button" @click.stop="$emit('toggle', openTopic === topic ? '' : topic)"
                class="settings-help-trigger" :class="[triggerClass, { 'is-open': openTopic === topic }]"
                :aria-expanded="openTopic === topic" :aria-label="label">
                <svg :class="iconClass" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                        d="M9.1 9a3 3 0 115.8 1.1c-.6 1.1-1.9 1.3-2.5 2.2-.3.4-.4.8-.4 1.2M12 17h.01"></path>
                </svg>
            </button>
            <div v-if="openTopic === topic" class="settings-help-popover" :class="popoverClass">
                <span class="settings-help-popover-content"><slot></slot></span>
            </div>`
    };

    const ModalShell = {
        inheritAttrs: false,
        props: {
            overlayClass: { type: [String, Array, Object], default: '' },
            panelClass: { type: [String, Array, Object], default: '' },
            closeOnBackdrop: Boolean
        },
        emits: ['close'],
        template: `
            <div :class="['fixed inset-0 flex items-center justify-center', overlayClass]"
                @click.self="closeOnBackdrop && $emit('close')">
                <div :class="panelClass"><slot></slot></div>
            </div>`
    };

    const ModalHeader = {
        emits: ['close'],
        template: `
            <div class="editor-modal-header">
                <slot></slot>
                <button @click="$emit('close')" class="modal-close-button">
                    <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
                    </svg>
                </button>
            </div>`
    };

    const UpdateNotificationModal = {
        props: {
            update: { type: Object, required: true },
            renderMarkdown: { type: Function, required: true }
        },
        setup(props, { expose }) {
            const show = ref(false);
            const countdown = ref(0);
            const scrolledToBottom = ref(false);
            const contentEl = ref(null);
            const remoteUpdateId = ref(null);
            const pendingRemoteUpdateId = ref(null);
            let countdownTimer = null;
            let countdownEndsAt = 0;
            let layoutTimer = null;

            const clearTimers = () => {
                clearInterval(countdownTimer);
                clearTimeout(layoutTimer);
                countdownTimer = null;
                layoutTimer = null;
            };
            const startCountdown = () => {
                clearInterval(countdownTimer);
                countdownEndsAt = Date.now() + 10_000;
                const updateCountdown = () => {
                    countdown.value = Math.max(0, Math.ceil((countdownEndsAt - Date.now()) / 1000));
                    if (countdown.value > 0) return;
                    clearInterval(countdownTimer);
                    countdownTimer = null;
                };
                updateCountdown();
                countdownTimer = setInterval(updateCountdown, 250);
            };
            const showRemoteUpdate = (versionId) => {
                clearTimers();
                remoteUpdateId.value = versionId;
                pendingRemoteUpdateId.value = null;
                countdown.value = 0;
                scrolledToBottom.value = true;
                show.value = true;
            };
            const handleRemoteUpdate = (event) => {
                const versionId = Number(event?.detail?.versionId);
                if (!Number.isInteger(versionId) || versionId < 10000 || versionId > 99999
                    || versionId <= Number(props.update.id)) return;
                if (remoteUpdateId.value !== null) {
                    remoteUpdateId.value = Math.max(remoteUpdateId.value, versionId);
                } else if (show.value) {
                    pendingRemoteUpdateId.value = Math.max(pendingRemoteUpdateId.value || 0, versionId);
                } else {
                    showRemoteUpdate(versionId);
                }
            };
            const check = () => {
                if (remoteUpdateId.value !== null || pendingRemoteUpdateId.value !== null) return;
                const lastId = Number.parseInt(localStorage.getItem('roleplay_hub_update_id'), 10);
                if (Number.isFinite(lastId) && lastId >= props.update.id) return;

                show.value = true;
                scrolledToBottom.value = false;
                startCountdown();
                layoutTimer = setTimeout(() => {
                    const element = contentEl.value;
                    if (element && element.scrollHeight <= element.clientHeight + 10) {
                        scrolledToBottom.value = true;
                    }
                }, 100);
            };
            const close = () => {
                if (countdown.value > 0) return;
                show.value = false;
                clearTimers();
                remoteUpdateId.value = null;
                localStorage.setItem('roleplay_hub_update_id', String(props.update.id));
                if (pendingRemoteUpdateId.value !== null) {
                    const versionId = pendingRemoteUpdateId.value;
                    layoutTimer = setTimeout(() => showRemoteUpdate(versionId), 150);
                }
            };
            const handleScroll = (event) => {
                const element = event.target;
                scrolledToBottom.value = element.scrollHeight - element.scrollTop - element.clientHeight < 10;
            };

            window.addEventListener('rphub:update-available', handleRemoteUpdate);
            expose({ check });
            onBeforeUnmount(() => {
                clearTimers();
                window.removeEventListener('rphub:update-available', handleRemoteUpdate);
            });
            return { contentEl, countdown, handleScroll, close, remoteUpdateId, scrolledToBottom, show };
        },
        template: `
            <modal-shell v-if="show" overlay-class="z-[80] bg-black/50 backdrop-blur-sm p-4 animate-fade-in"
                panel-class="bg-white rounded-xl border border-gray-200 w-full max-w-lg flex flex-col shadow-2xl transform transition-all scale-100 overflow-hidden relative">
                    <div class="bg-gradient-to-r from-primary-50 to-purple-50 p-4 border-b border-gray-100">
                        <div class="flex items-center gap-3">
                            <h3 class="text-xl font-bold text-gray-900">{{ remoteUpdateId ? '发现新版本' : update.title }}</h3>
                            <span class="bg-primary-100 text-primary-600 text-[10px] font-bold px-2 py-0.5 rounded-full border border-primary-200 transform translate-y-0.5">New</span>
                        </div>
                    </div>
                    <div ref="contentEl" class="p-4 max-h-[75vh] overflow-y-auto custom-scrollbar update-content" @scroll="handleScroll">
                        <div v-if="remoteUpdateId" class="py-6 text-center">
                            <p class="text-lg font-bold text-gray-800">发现新版本，手动刷新页面后更新</p>
                        </div>
                        <div v-else class="prose prose-sm prose-gray max-w-none">
                            <div class="markdown-body" v-html="renderMarkdown(update.content, 'assistant', true)"></div>
                        </div>
                        <div class="mt-8 mb-2 flex justify-end">
                            <button @click="close" :disabled="!remoteUpdateId && countdown > 0"
                                :class="{ 'opacity-50 cursor-not-allowed': !remoteUpdateId && countdown > 0 }"
                                class="px-10 py-2.5 bg-primary-600 hover:bg-primary-700 text-white font-medium rounded-lg shadow-sm hover:shadow transition-all active:scale-95">
                                知道了 <span v-if="!remoteUpdateId && countdown > 0">({{ countdown }}s)</span>
                            </button>
                        </div>
                    </div>
                    <div v-show="!remoteUpdateId && !scrolledToBottom" class="absolute bottom-0 left-0 right-0 pt-12 pb-4 bg-gradient-to-t from-white via-white/80 to-transparent flex justify-center items-end pointer-events-none transition-opacity duration-300 rounded-b-xl">
                        <div class="text-xs text-blue-500 flex items-center gap-1 animate-bounce bg-white shadow-sm border border-blue-100 px-3 py-1.5 rounded-full">
                            <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 14l-7 7m0 0l-7-7m7 7V3"></path></svg>
                            向下滑动查看完整内容
                        </div>
                    </div>
            </modal-shell>`
    };

    const StatusNoticeModal = {
        props: {
            show: Boolean,
            title: { type: String, required: true },
            message: { type: String, required: true },
            buttonLabel: { type: String, default: '我知道了' }
        },
        emits: ['close'],
        template: `
            <modal-shell v-if="show" overlay-class="z-[80] bg-black/50 backdrop-blur-sm p-4 animate-fade-in"
                panel-class="bg-white rounded-xl border border-gray-200 w-full max-w-sm flex flex-col shadow-2xl transform transition-all scale-100 overflow-hidden">
                    <div class="bg-gradient-to-r from-primary-50 to-purple-50 p-6 flex flex-col items-center justify-center text-center border-b border-gray-100">
                        <div class="w-12 h-12 bg-white rounded-full flex items-center justify-center shadow-sm mb-3">
                            <svg class="w-6 h-6 text-primary-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path>
                            </svg>
                        </div>
                        <h3 class="text-lg font-bold text-gray-900 mb-1">{{ title }}</h3>
                        <p class="text-sm text-gray-500">{{ message }}</p>
                    </div>
                    <div class="bg-gray-50 p-4 flex justify-center border-t border-gray-100">
                        <button @click="$emit('close')" class="px-6 py-2 bg-primary-600 hover:bg-primary-700 text-white font-medium rounded-lg shadow-sm hover:shadow transition-all active:scale-95 w-full">
                            {{ buttonLabel }}
                        </button>
                    </div>
            </modal-shell>`
    };

    const UserSetupModal = {
        props: {
            show: Boolean,
            name: { type: String, default: '' },
            description: { type: String, default: '' },
            person: { type: String, default: 'second' }
        },
        emits: ['update:name', 'update:description', 'update:person', 'save'],
        template: `
            <modal-shell v-if="show" overlay-class="z-[70] bg-black/50 backdrop-blur-sm p-4 animate-fade-in"
                panel-class="bg-white rounded-xl border border-gray-200 w-full max-w-md flex flex-col shadow-2xl transform transition-all scale-100">
                    <div class="p-6">
                        <div class="flex items-center justify-center w-12 h-12 rounded-full bg-primary-100 text-primary-600 mb-4 mx-auto">
                            <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"></path>
                            </svg>
                        </div>
                        <h3 class="text-xl font-bold text-gray-900 mb-2 text-center">欢迎使用 RP Hub</h3>
                        <p class="text-sm text-gray-500 mb-6 text-center">为了获得更好的沉浸式体验，请先进行个性化设置。</p>
                        <div class="space-y-4">
                            <div>
                                <label class="block text-sm font-medium text-gray-700 mb-1">您的称呼 <span class="text-red-500">*</span></label>
                                <input :value="name" @input="$emit('update:name', $event.target.value)" type="text"
                                    class="w-full bg-gray-50/60 border border-gray-300 rounded-lg px-4 py-2 text-gray-800 focus:ring-2 focus:ring-primary-500 focus:border-primary-500 focus:outline-none transition-all"
                                    placeholder="角色对您的称呼">
                            </div>
                            <div>
                                <label class="block text-sm font-medium text-gray-700 mb-2">旁白叙事视角</label>
                                <div class="segmented-switch">
                                    <div class="segmented-switch__indicator" :class="{ 'is-right': person === 'third' }"></div>
                                    <button @click="$emit('update:person', 'second')" class="segmented-switch__option" :class="{ 'is-active': person === 'second' }">第二人称 (你)</button>
                                    <button @click="$emit('update:person', 'third')" class="segmented-switch__option" :class="{ 'is-active': person === 'third' }">第三人称 ({{ name || '您的称呼' }})</button>
                                </div>
                                <p class="mt-1.5 text-[11px] text-gray-400 px-1"></p>
                            </div>
                            <div>
                                <label class="block text-sm font-medium text-gray-700 mb-1">详细设定 (可选)</label>
                                <textarea :value="description" @input="$emit('update:description', $event.target.value)" rows="6"
                                    class="w-full bg-gray-50/60 border border-gray-300 rounded-lg px-4 py-2 text-gray-800 focus:ring-2 focus:ring-primary-500 focus:border-primary-500 focus:outline-none transition-all resize-y"
                                    placeholder="例如：您的外貌、性格、人设等..."></textarea>
                            </div>
                        </div>
                    </div>
                    <div class="bg-gray-50 px-4 py-3 sm:px-6 flex flex-row-reverse rounded-b-xl">
                        <button @click="$emit('save')" :disabled="!name || name === '请前往设置自定义你的名称'" type="button"
                            class="w-full inline-flex justify-center rounded-lg border border-transparent shadow-sm px-4 py-2 bg-primary-600 text-base font-medium text-white hover:bg-primary-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary-500 sm:ml-3 sm:w-auto sm:text-sm transition-colors disabled:opacity-50 disabled:cursor-not-allowed">保存并开始</button>
                    </div>
            </modal-shell>`
    };

    const ModelSelectorModal = {
        props: {
            show: Boolean,
            target: { type: String, default: 'model' },
            searchQuery: { type: String, default: '' },
            activeTag: { type: String, default: 'all' },
            tags: { type: Array, default: () => [] },
            models: { type: Array, default: () => [] },
            currentModel: { type: String, default: '' },
            slotModels: { type: Array, default: () => [] }
        },
        emits: ['close', 'select', 'select-slots', 'update:search-query', 'update:active-tag'],
        data() {
            return {
                activeSlot: 0,
                draftSlotModels: ['', '', '']
            };
        },
        watch: {
            show(visible) {
                if (visible && this.target === 'quickModels') {
                    this.activeSlot = 0;
                    this.draftSlotModels = [0, 1, 2].map(index => this.slotModels[index] || '');
                }
            }
        },
        methods: {
            chooseModel(modelId) {
                if (this.target !== 'quickModels') {
                    this.$emit('select', modelId);
                    return;
                }
                this.draftSlotModels[this.activeSlot] = this.draftSlotModels[this.activeSlot] === modelId ? '' : modelId;
                this.draftSlotModels = [...this.draftSlotModels];
                this.$emit('select-slots', [...this.draftSlotModels]);
            }
        },
        template: `
            <transition name="fade">
                <modal-shell v-if="show" overlay-class="z-50 bg-black/50 backdrop-blur-sm p-4"
                    panel-class="bg-white rounded-xl border border-gray-200 w-full max-w-2xl max-h-[90vh] h-[90vh] flex flex-col shadow-2xl transform transition-all scale-100">
                        <div class="p-4 border-b border-gray-100 flex justify-between items-center">
                            <h3 class="text-lg font-bold text-gray-800">{{ target === 'quickModels' ? '聊天模型' : '选择模型' }}</h3>
                            <button @click="$emit('close')" class="text-gray-400 hover:text-gray-600 focus:outline-none transition-colors">
                                <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
                                </svg>
                            </button>
                        </div>
                        <div v-if="target === 'quickModels'" class="grid grid-cols-3 gap-2 p-4 pb-0">
                            <button v-for="(_, index) in draftSlotModels" :key="index" type="button"
                                @click="activeSlot = index"
                                :class="['min-w-0 rounded-xl border px-3 py-2.5 text-left transition-colors', activeSlot === index ? 'border-primary-300 bg-primary-50 text-primary-700' : 'border-gray-200 bg-white text-gray-500 hover:bg-gray-50']">
                                <span class="block text-xs font-bold mb-1">槽位 {{ index + 1 }}</span>
                                <span class="block truncate text-[11px] font-mono" :title="draftSlotModels[index]">{{ draftSlotModels[index] || '未选择' }}</span>
                            </button>
                        </div>
                        <div class="p-4 border-b border-gray-100 flex flex-col gap-3">
                            <input :value="searchQuery" @input="$emit('update:search-query', $event.target.value)" type="text"
                                :placeholder="target === 'memoryEmbeddingModel' ? '已锁定：embedding' : '检索模型...'"
                                :readonly="target === 'memoryEmbeddingModel'"
                                :title="target === 'memoryEmbeddingModel' ? '模型选择已锁定' : ''"
                                :class="['w-full border rounded-lg px-4 py-2 focus:outline-none transition-all shadow-sm', target === 'memoryEmbeddingModel' ? 'bg-gray-100 border-gray-200 text-gray-400 placeholder-gray-400 cursor-not-allowed shadow-none select-none' : 'bg-gray-50/60 border-gray-300 text-gray-800 focus:ring-2 focus:ring-primary-500 focus:shadow-md']">
                            <div class="flex flex-wrap gap-2 max-h-[160px] overflow-y-auto custom-scrollbar items-center py-1">
                                <button v-for="tag in tags" :key="tag.name" @click="$emit('update:active-tag', tag.name)" :class="[
                                    'flex items-center px-3.5 py-1.5 text-xs font-bold rounded-full transition-all border outline-none active:scale-95 whitespace-nowrap',
                                    activeTag === tag.name
                                        ? 'bg-primary-50 text-primary-700 border-primary-300 shadow-sm'
                                        : 'bg-white text-gray-500 border-gray-200 hover:bg-gray-50 hover:text-gray-700 hover:border-gray-300 shadow-sm'
                                ]">
                                    <span class="leading-none">{{ tag.name === 'all' ? '全部' : (tag.name === 'other' ? '其他' : tag.name.toUpperCase()) }}</span>
                                    <span class="ml-1.5 opacity-60 font-mono text-[11px] leading-none">{{ tag.count }}</span>
                                </button>
                            </div>
                        </div>
                        <div class="flex-1 overflow-y-auto p-2 min-h-[300px]">
                            <div v-if="models.length === 0" class="flex flex-col items-center justify-center py-12 text-gray-400">
                                <svg class="w-12 h-12 mb-3 opacity-20" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19.428 15.428a2 2 0 00-1.022-.547l-2.387-.477a6 6 0 00-3.86.517l-.318.158a6 6 0 01-3.86.517L6.05 15.21a2 2 0 00-1.806.547M8 4h8l-1 1v5.172a2 2 0 00.586 1.414l5 5c1.26 1.26.367 3.414-1.415 3.414H4.828c-1.782 0-2.674-2.154-1.414-3.414l5-5A2 2 0 009 10.172V5L8 4z"></path>
                                </svg>
                                未找到模型或正在加载...
                            </div>
                            <div class="space-y-1">
                                <button v-for="model in models" :key="model.id" @click="chooseModel(model.id)"
                                    class="w-full text-left px-4 py-3 rounded-xl hover:bg-gray-50 hover:shadow-[0_2px_4px_rgba(0,0,0,0.02)] transition-colors flex justify-between items-center group border border-transparent hover:border-gray-100 active:bg-gray-100">
                                    <span class="text-gray-700 font-mono font-medium group-hover:text-primary-600 transition-colors">{{ model.id }}</span>
                                    <span v-if="(target === 'quickModels' ? draftSlotModels[activeSlot] : currentModel) === model.id" class="text-primary-600 bg-primary-50 p-1 rounded-full shadow-sm">
                                        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path>
                                        </svg>
                                    </span>
                                </button>
                            </div>
                        </div>
                </modal-shell>
            </transition>`
    };

    const PaginationControls = {
        props: {
            current: { type: Number, required: true },
            total: { type: Number, required: true },
            label: { type: String, default: '分页' }
        },
        emits: ['change'],
        template: `
            <nav v-if="total > 1" class="flex items-center justify-center gap-2 pt-1" :aria-label="label">
                <button type="button" @click="$emit('change', current - 1)" :disabled="current === 1" class="pagination-button">
                    <svg class="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"></path>
                    </svg>
                    上一页
                </button>
                <span class="min-w-[58px] text-center text-xs font-mono text-gray-500">{{ current }} / {{ total }}</span>
                <button type="button" @click="$emit('change', current + 1)" :disabled="current === total" class="pagination-button">
                    下一页
                    <svg class="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"></path>
                    </svg>
                </button>
            </nav>`
    };

    const AddCharacterModal = {
        props: { show: Boolean },
        emits: ['close', 'create', 'import-character'],
        template: `
            <modal-shell v-if="show" close-on-backdrop @close="$emit('close')"
                overlay-class="z-[60] bg-black/50 backdrop-blur-sm p-4 animate-fade-in"
                panel-class="compact-modal-panel">
                    <div class="p-6">
                        <h3 class="text-xl font-bold text-gray-900 mb-6 flex items-center">
                            <svg class="w-6 h-6 mr-2 text-primary-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z"></path>
                            </svg>
                            添加角色卡
                        </h3>
                        <div class="grid grid-cols-1 gap-3">
                            <button @click="$emit('create')" class="choice-card group">
                                <div class="choice-card__icon">
                                    <svg class="w-6 h-6 text-primary-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"></path>
                                    </svg>
                                </div>
                                <div class="text-left">
                                    <div class="font-bold">新建角色卡</div>
                                    <div class="text-xs text-gray-500">从零开始创建一个新角色</div>
                                </div>
                            </button>
                            <label class="choice-card group">
                                <div class="choice-card__icon">
                                    <svg class="w-6 h-6 text-primary-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"></path>
                                    </svg>
                                </div>
                                <div class="text-left flex-1">
                                    <div class="font-bold">导入角色卡</div>
                                    <div class="text-xs text-gray-500">导入 .png 或 .json 文件</div>
                                </div>
                                <input type="file" accept=".png,.json" @change="$emit('import-character', $event)" class="hidden">
                            </label>
                            <label class="choice-card group">
                                <div class="choice-card__icon">
                                    <svg class="w-6 h-6 text-primary-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z"></path>
                                    </svg>
                                </div>
                                <div class="text-left flex-1">
                                    <div class="font-bold">导入聊天记录</div>
                                    <div class="text-xs text-gray-500">支持全部分支与旧版 .jsonl 聊天数据</div>
                                </div>
                                <input type="file" accept=".jsonl" @change="$emit('import-character', $event)" class="hidden">
                            </label>
                        </div>
                        <button @click="$emit('close')" class="mt-6 w-full py-3 text-red-500 font-medium hover:text-red-600 transition-colors">取消</button>
                    </div>
            </modal-shell>`
    };

    const AutoImageGenModal = {
        props: { show: Boolean },
        emits: ['decide'],
        template: `
            <modal-shell v-if="show" overlay-class="z-[90] bg-black/50 backdrop-blur-sm p-4 animate-fade-in"
                panel-class="bg-white rounded-2xl border border-gray-200 w-full max-w-md flex flex-col shadow-2xl transform transition-all scale-100 overflow-hidden">
                    <div class="bg-gradient-to-r from-primary-50 to-blue-50 p-6 border-b border-gray-100">
                        <div class="flex items-center gap-3">
                            <div class="w-10 h-10 rounded-full bg-primary-100 text-primary-600 flex items-center justify-center">
                                <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"></path>
                                </svg>
                            </div>
                            <h3 class="text-xl font-bold text-gray-900">自动生图</h3>
                        </div>
                    </div>
                    <div class="p-6 space-y-4">
                        <p class="text-gray-600 leading-relaxed text-center text-lg font-medium">是否为此角色卡开启 <span class="font-bold text-primary-600">自动生图</span> 功能？</p>
                        <div class="bg-yellow-50 border border-yellow-200 rounded-xl p-4">
                            <div class="flex items-start mb-3">
                                <div class="flex-shrink-0">
                                    <svg class="h-5 w-5 text-yellow-500 mt-0.5" viewBox="0 0 20 20" fill="currentColor">
                                        <path fill-rule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clip-rule="evenodd"></path>
                                    </svg>
                                </div>
                                <h4 class="ml-2 text-sm font-bold text-yellow-800">注意事项</h4>
                            </div>
                            <ul class="list-disc list-outside ml-9 space-y-1.5 text-sm text-yellow-700">
                                <li>您可以在 “世界书 -> 自动生图” 手动管理此功能。</li>
                                <li>前往 “设置” 可以切换生图版本、风格与比例。</li>
                            </ul>
                        </div>
                    </div>
                    <div class="bg-gray-50 p-4 flex justify-end gap-3 rounded-b-2xl border-t border-gray-100">
                        <button @click="$emit('decide', false)" class="px-5 py-2.5 bg-white hover:bg-gray-50 text-gray-700 font-medium rounded-xl border border-gray-200 transition-all active:scale-95">暂不开启</button>
                        <button @click="$emit('decide', true)" class="px-6 py-2.5 bg-primary-600 hover:bg-primary-700 text-white font-bold rounded-xl shadow-sm hover:shadow-md transition-all active:scale-95">立即开启</button>
                    </div>
            </modal-shell>`
    };

    const ActiveToolEditorModal = {
        props: {
            show: Boolean,
            tool: { type: Object, required: true },
            displayDescription: { type: String, default: '' },
            webTool: Boolean,
            minResultCount: { type: Number, required: true },
            maxResultCount: { type: Number, required: true }
        },
        emits: ['close', 'save', 'update:result-count', 'update:tavily-api-key'],
        template: `
            <modal-shell v-if="show" overlay-class="z-50 bg-black/50 backdrop-blur-sm p-4 animate-fade-in"
                panel-class="bg-white rounded-2xl border border-gray-200 w-full max-w-3xl flex flex-col shadow-2xl max-h-[90vh] overflow-hidden">
                    <modal-header @close="$emit('close')">
                        <div class="flex items-center gap-3">
                            <div class="p-2 bg-primary-50 text-primary-600 rounded-lg">
                                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M14.7 6.3a1 1 0 000 1.4l1.6 1.6a1 1 0 001.4 0l3.77-3.77a6 6 0 01-7.94 7.94l-6.91 6.91a2.12 2.12 0 01-3-3l6.91-6.91a6 6 0 017.94-7.94L14.7 6.3z"></path></svg>
                            </div>
                            <div>
                                <h3 class="text-lg font-bold text-gray-800 leading-tight">编辑工具</h3>
                                <p class="text-xs text-gray-500">{{ tool.name || '未命名工具' }}</p>
                            </div>
                        </div>
                    </modal-header>
                    <div class="flex-1 overflow-y-auto custom-scrollbar p-6 space-y-6 bg-gray-50/30">
                        <div class="max-w-2xl mx-auto text-center">
                            <div class="w-14 h-14 mx-auto rounded-2xl bg-primary-50 text-primary-600 flex items-center justify-center shadow-sm border border-primary-100">
                                <svg class="w-7 h-7" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M14.7 6.3a1 1 0 000 1.4l1.6 1.6a1 1 0 001.4 0l3.77-3.77a6 6 0 01-7.94 7.94l-6.91 6.91a2.12 2.12 0 01-3-3l6.91-6.91a6 6 0 017.94-7.94L14.7 6.3z"></path></svg>
                            </div>
                            <h3 class="mt-4 text-xl md:text-2xl font-bold text-gray-900 leading-tight">{{ tool.name || '未命名工具' }}</h3>
                            <p class="mt-3 text-sm text-gray-500 leading-relaxed whitespace-pre-wrap">{{ displayDescription }}</p>
                        </div>
                        <div class="max-w-2xl mx-auto bg-white border border-gray-200 rounded-2xl p-5 md:p-6 shadow-sm">
                            <div class="flex items-center justify-between gap-4 mb-5">
                                <div>
                                    <div class="text-sm font-bold text-gray-800">返回条数</div>
                                    <div class="text-xs text-gray-500 mt-1">控制每次工具检索返回的内容条数</div>
                                </div>
                                <div class="text-xs font-mono text-primary-600 bg-primary-50 px-1.5 py-0.5 rounded border border-primary-100">{{ tool.resultCount || 8 }} 条</div>
                            </div>
                            <input :value="tool.resultCount" @input="$emit('update:result-count', Number($event.target.value))" type="range"
                                :min="minResultCount" :max="maxResultCount" step="1"
                                class="w-full h-2 bg-gray-200 rounded-lg appearance-none cursor-pointer accent-primary-600">
                            <div class="mt-2 flex justify-between text-[11px] text-gray-400 font-medium">
                                <span>{{ minResultCount }} 条</span><span>{{ maxResultCount }} 条</span>
                            </div>
                        </div>
                        <div v-if="webTool" class="max-w-2xl mx-auto bg-white border border-gray-200 rounded-2xl p-5 md:p-6 shadow-sm space-y-5">
                            <div>
                                <label class="block text-xs font-bold text-gray-500 uppercase tracking-wide mb-1.5">Tavily API Key</label>
                                <input :value="tool.tavilyApiKey" @input="$emit('update:tavily-api-key', $event.target.value.trim())" type="password"
                                    class="w-full bg-white border border-gray-200 rounded-xl px-3 py-2 text-sm text-gray-800 focus:ring-4 focus:ring-primary-500/10 focus:border-primary-500 focus:outline-none transition-all" placeholder="tvly-...">
                            </div>
                        </div>
                    </div>
                    <div class="p-4 md:p-5 border-t border-gray-100 flex justify-end space-x-3 bg-gray-50/80 backdrop-blur-sm flex-shrink-0">
                        <button @click="$emit('close')" class="modal-secondary-button">取消</button>
                        <button @click="$emit('save')" class="modal-primary-button">
                            <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path></svg>
                            保存工具
                        </button>
                    </div>
            </modal-shell>`
    };

    const PresetEditorModal = {
        components: { CustomSelect },
        props: {
            show: Boolean,
            preset: { type: Object, required: true },
            editing: Boolean,
            roleOptions: { type: Array, default: () => [] },
            roleLabel: { type: String, default: '' }
        },
        emits: ['close', 'save', 'update:name', 'update:role', 'update:content'],
        template: `
            <modal-shell v-if="show" overlay-class="z-50 bg-black/50 backdrop-blur-sm p-2 md:p-3 animate-fade-in"
                panel-class="bg-white rounded-2xl border border-gray-200 w-full max-w-2xl flex flex-col shadow-2xl max-h-[94vh] overflow-hidden">
                    <modal-header @close="$emit('close')">
                        <div class="flex items-center gap-3">
                            <div class="p-2 bg-primary-50 text-primary-600 rounded-lg">
                                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6V4m0 2a2 2 0 100 4m0-4a2 2 0 110 4m-6 8a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4m6 6v10m6-2a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4"></path></svg>
                            </div>
                            <div>
                                <h3 class="text-lg font-bold text-gray-800 leading-tight">{{ editing ? '编辑预设' : '新建预设' }}</h3>
                                <p class="text-xs text-gray-500">{{ roleLabel }}</p>
                            </div>
                        </div>
                    </modal-header>
                    <div class="flex-1 p-6 space-y-6 bg-gray-50/30 overflow-y-auto custom-scrollbar">
                        <div>
                            <label class="block text-xs font-bold text-gray-500 uppercase tracking-wide mb-1.5">预设名称</label>
                            <input :value="preset.name" @input="$emit('update:name', $event.target.value)" type="text"
                                class="w-full bg-white border border-gray-200 rounded-xl px-4 py-2.5 text-gray-800 focus:ring-2 focus:ring-primary-500 focus:border-primary-500 focus:outline-none transition-all shadow-sm font-medium" placeholder="例如：沉浸式叙事">
                        </div>
                        <div>
                            <label class="block text-xs font-bold text-gray-500 uppercase tracking-wide mb-1.5">注入位置</label>
                            <custom-select :model-value="preset.role" @update:modelValue="$emit('update:role', $event)" :options="roleOptions" button-class="font-medium"></custom-select>
                        </div>
                        <div>
                            <label class="block text-xs font-bold text-gray-500 uppercase tracking-wide mb-1.5 flex justify-between">
                                <span>{{ roleLabel }}内容</span>
                                <span class="text-[10px] font-normal normal-case bg-gray-100 px-1.5 rounded text-gray-500">{{ (preset.content || '').length }} 字符</span>
                            </label>
                            <textarea :value="preset.content" @input="$emit('update:content', $event.target.value)" rows="12"
                                class="w-full bg-white border border-gray-200 rounded-xl px-4 py-3 text-gray-800 focus:ring-2 focus:ring-primary-500 focus:border-primary-500 focus:outline-none text-sm shadow-inner leading-relaxed resize-y min-h-[200px]" placeholder="在此输入预设内容..."></textarea>
                        </div>
                    </div>
                    <div class="p-4 md:p-5 border-t border-gray-100 flex justify-end space-x-3 bg-gray-50/80 backdrop-blur-sm flex-shrink-0">
                        <button @click="$emit('close')" class="modal-secondary-button">取消</button>
                        <button @click="$emit('save')" class="modal-primary-button">
                            <svg class="w-4 h-4 mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path></svg>
                            保存预设
                        </button>
                    </div>
            </modal-shell>`
    };

    const CharacterEditorModal = {
        props: {
            show: Boolean,
            character: { type: Object, required: true },
            editing: Boolean,
            tab: { type: String, default: 'basic' }
        },
        emits: ['close', 'save', 'avatar-upload', 'update:tab', 'update-field'],
        data: () => ({
            tabs: [
                { value: 'basic', label: '基础' },
                { value: 'description', label: '描述' },
                { value: 'personality', label: '人设' },
                { value: 'first_mes', label: '开场白' }
            ]
        }),
        methods: {
            updateField(field, value) {
                this.$emit('update-field', { field, value });
            }
        },
        template: `
            <modal-shell v-if="show" overlay-class="z-50 bg-black/50 backdrop-blur-sm p-0 md:p-4 animate-fade-in"
                panel-class="bg-white md:rounded-2xl border-0 md:border border-gray-200 w-full max-w-2xl h-full md:h-[750px] flex flex-col shadow-2xl overflow-hidden">
                    <div class="p-3 md:p-5 border-b border-gray-100 flex flex-col gap-4 bg-gray-50/80 backdrop-blur-sm flex-shrink-0">
                        <div class="flex justify-between items-center">
                            <h3 class="text-lg md:text-xl font-bold text-gray-800 flex items-center">
                                <svg class="w-5 h-5 mr-2 text-primary-600" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"></path></svg>
                                {{ editing ? '编辑角色' : '新建角色' }}
                            </h3>
                            <button @click="$emit('close')" class="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-200/50 rounded-full transition-all">
                                <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>
                            </button>
                        </div>
                        <div class="segmented-switch segmented-switch--compact segmented-switch--four w-full">
                            <div class="segmented-switch__indicator" :class="{
                                'is-position-2': tab === 'description',
                                'is-position-3': tab === 'personality',
                                'is-position-4': tab === 'first_mes'
                            }"></div>
                            <button v-for="item in tabs" :key="item.value" @click="$emit('update:tab', item.value)"
                                class="segmented-switch__option" :class="{ 'is-active': tab === item.value }">
                                <span>{{ item.label }}</span>
                            </button>
                        </div>
                    </div>

                    <div class="flex-1 overflow-y-auto p-3 md:p-8 custom-scrollbar flex flex-col bg-gray-50/30">
                        <div v-if="tab === 'basic'" class="animate-fade-in flex-1 flex flex-col gap-4 md:gap-6 items-center justify-center min-h-0">
                            <div class="flex flex-col items-center flex-shrink min-h-0">
                                <div class="w-auto h-[40vh] md:h-[45vh] aspect-[2/3] bg-gray-100 rounded-2xl border border-gray-200 overflow-hidden relative group shadow-xl ring-4 ring-white">
                                    <img :src="character.avatar" class="w-full h-full object-cover">
                                    <label class="absolute inset-0 bg-black/50 flex flex-col items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity cursor-pointer backdrop-blur-[2px]">
                                        <svg class="w-8 h-8 text-white mb-2" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"></path></svg>
                                        <span class="text-white font-bold text-sm">更换图片</span>
                                        <input type="file" accept="image/*" @change="$emit('avatar-upload', $event)" class="hidden">
                                    </label>
                                </div>
                            </div>
                            <div class="w-full max-w-md space-y-2 flex-shrink-0">
                                <div class="text-center">
                                    <label class="block text-[10px] font-bold text-gray-400 mb-1 uppercase tracking-wider">角色名称</label>
                                    <input :value="character.name" @input="updateField('name', $event.target.value)" type="text"
                                        class="w-full bg-gray-50/60 border border-gray-300 rounded-xl px-4 py-2.5 text-gray-800 focus:ring-2 focus:ring-primary-500 focus:border-primary-500 focus:outline-none text-lg font-bold shadow-inner transition-all text-center" placeholder="输入角色名称...">
                                </div>
                            </div>
                        </div>

                        <div v-if="tab === 'description'" class="animate-fade-in h-full flex flex-col">
                            <div class="flex justify-between items-center mb-2"><label class="block text-sm font-bold text-gray-600">简短描述</label><span class="text-[10px] text-gray-400 font-mono bg-gray-100 px-1.5 py-0.5 rounded">{{ (character.description || '').length }} 字</span></div>
                            <textarea :value="character.description" @input="updateField('description', $event.target.value)"
                                class="w-full bg-gray-50/60 border border-gray-300 rounded-xl px-4 py-3 text-gray-800 focus:ring-2 focus:ring-primary-500 focus:border-primary-500 focus:outline-none flex-1 resize-none shadow-inner transition-all leading-relaxed" placeholder="对角色的简短介绍..."></textarea>
                        </div>
                        <div v-if="tab === 'personality'" class="animate-fade-in h-full flex flex-col">
                            <div class="flex justify-between items-center mb-2"><label class="block text-sm font-bold text-gray-600">具体人设</label><span class="text-[10px] text-gray-400 font-mono bg-gray-100 px-1.5 py-0.5 rounded">{{ (character.personality || '').length }} 字</span></div>
                            <textarea :value="character.personality" @input="updateField('personality', $event.target.value)"
                                class="w-full bg-gray-50/60 border border-gray-300 rounded-xl px-4 py-3 text-gray-800 focus:ring-2 focus:ring-primary-500 focus:border-primary-500 focus:outline-none flex-1 resize-none shadow-inner transition-all leading-relaxed" placeholder="详细的角色性格、外貌、喜好等设定..."></textarea>
                        </div>
                        <div v-if="tab === 'first_mes'" class="animate-fade-in h-full flex flex-col">
                            <div class="flex justify-between items-center mb-2"><label class="block text-sm font-bold text-gray-600">开场白</label><span class="text-[10px] text-gray-400 font-mono bg-gray-100 px-1.5 py-0.5 rounded">{{ (character.first_mes || '').length }} 字</span></div>
                            <textarea :value="character.first_mes" @input="updateField('first_mes', $event.target.value)"
                                class="w-full bg-gray-50/60 border border-gray-300 rounded-xl px-4 py-3 text-gray-800 focus:ring-2 focus:ring-primary-500 focus:border-primary-500 focus:outline-none flex-1 resize-none shadow-inner transition-all leading-relaxed" placeholder="角色在对话开始时说的第一句话..."></textarea>
                        </div>
                    </div>

                    <div class="p-3 md:p-5 border-t border-gray-100 flex justify-end space-x-3 bg-gray-50/80 backdrop-blur-sm flex-shrink-0">
                        <button @click="$emit('close')" class="px-6 py-2.5 bg-white hover:bg-gray-100 text-gray-700 border border-gray-300 rounded-xl transition-all font-bold text-sm shadow-sm active:scale-95">取消</button>
                        <button @click="$emit('save')" class="px-8 py-2.5 bg-primary-600 hover:bg-primary-700 text-white rounded-xl transition-all font-bold text-sm shadow-md hover:shadow-lg active:scale-95 flex items-center">
                            <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path></svg>
                            保存角色
                        </button>
                    </div>
            </modal-shell>`
    };

    const RegexEditorModal = {
        components: { CustomSelect },
        props: {
            show: Boolean,
            script: { type: Object, required: true },
            editing: Boolean,
            scopeOptions: { type: Array, default: () => [] }
        },
        emits: ['close', 'save', 'update-field'],
        methods: {
            updateField(field, value) {
                this.$emit('update-field', { field, value });
            },
            togglePlacement(value) {
                const placement = Array.isArray(this.script.placement) ? [...this.script.placement] : [];
                const index = placement.indexOf(value);
                if (index === -1) placement.push(value);
                else placement.splice(index, 1);
                this.updateField('placement', placement);
            },
            toggleMode(key, checked) {
                this.updateField(key, checked);
                if (checked) this.updateField(key === 'markdownOnly' ? 'promptOnly' : 'markdownOnly', false);
            },
            updateNumber(field, value) {
                this.updateField(field, value === '' ? '' : Number(value));
            }
        },
        template: `
            <modal-shell v-if="show" overlay-class="z-50 bg-black/50 backdrop-blur-sm p-2 md:p-3 animate-fade-in"
                panel-class="bg-white rounded-2xl border border-gray-200 w-full max-w-lg flex flex-col shadow-2xl max-h-[94vh] overflow-hidden">
                    <modal-header @close="$emit('close')">
                        <div class="flex items-center gap-3">
                            <div class="p-2 bg-primary-50 text-primary-600 rounded-lg">
                                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4"></path></svg>
                            </div>
                            <div>
                                <h3 class="text-lg font-bold text-gray-800 leading-tight">{{ editing ? '编辑正则脚本' : '新建正则脚本' }}</h3>
                                <p class="text-xs text-gray-500">匹配与替换规则</p>
                            </div>
                        </div>
                    </modal-header>

                    <div class="flex-1 overflow-y-auto custom-scrollbar p-6 space-y-6 bg-gray-50/30">
                        <div>
                            <label class="block text-xs font-bold text-gray-500 uppercase tracking-wide mb-1.5">脚本名称</label>
                            <input :value="script.name" @input="updateField('name', $event.target.value)" type="text"
                                class="w-full bg-white border border-gray-200 rounded-xl px-4 py-2.5 text-gray-800 focus:ring-2 focus:ring-primary-500 focus:border-primary-500 focus:outline-none transition-all shadow-sm" placeholder="例如：去除多余空行">
                        </div>
                        <div>
                            <label class="block text-xs font-bold text-gray-500 uppercase tracking-wide mb-1.5">作用范围</label>
                            <custom-select :model-value="script.scope" @update:modelValue="updateField('scope', $event)" :options="scopeOptions" button-class="bg-white"></custom-select>
                        </div>

                        <div class="grid grid-cols-4 gap-3">
                            <div class="col-span-3">
                                <label class="block text-xs font-bold text-gray-500 uppercase tracking-wide mb-1.5">正则表达式</label>
                                <div class="relative">
                                    <span class="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 font-mono text-sm">/</span>
                                    <input :value="script.regex" @input="updateField('regex', $event.target.value)" type="text"
                                        class="w-full bg-white border border-gray-200 rounded-xl pl-6 pr-4 py-2.5 text-gray-800 font-mono text-sm focus:ring-2 focus:ring-primary-500 focus:border-primary-500 focus:outline-none transition-all shadow-sm" placeholder="pattern">
                                    <span class="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 font-mono text-sm">/</span>
                                </div>
                            </div>
                            <div class="col-span-1">
                                <label class="block text-xs font-bold text-gray-500 uppercase tracking-wide mb-1.5">Flags</label>
                                <input :value="script.flags" @input="updateField('flags', $event.target.value)" type="text"
                                    class="w-full bg-white border border-gray-200 rounded-xl px-3 py-2.5 text-gray-800 font-mono text-sm focus:ring-2 focus:ring-primary-500 focus:border-primary-500 focus:outline-none transition-all shadow-sm text-center" placeholder="gim">
                            </div>
                        </div>

                        <div>
                            <label class="block text-xs font-bold text-gray-500 uppercase tracking-wide mb-1.5">替换内容</label>
                            <textarea :value="script.replacement" @input="updateField('replacement', $event.target.value)" rows="9"
                                class="w-full bg-white border border-gray-200 rounded-xl px-4 py-2.5 text-gray-800 font-mono text-sm focus:ring-2 focus:ring-primary-500 focus:border-primary-500 focus:outline-none transition-all shadow-sm resize-y min-h-[190px]" placeholder="支持 $1, $2 等捕获组引用"></textarea>
                        </div>

                        <details class="group border border-gray-200 rounded-xl bg-white shadow-sm overflow-hidden">
                            <summary class="flex items-center justify-between p-4 cursor-pointer bg-gray-50 hover:bg-gray-100 transition-colors select-none">
                                <span class="text-sm font-bold text-gray-700 flex items-center">
                                    <svg class="w-4 h-4 mr-2 text-gray-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6V4m0 2a2 2 0 100 4m0-4a2 2 0 110 4m-6 8a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4m6 6v10m6-2a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4"></path></svg>
                                    高级选项 (生效位置、深度、模式)
                                </span>
                                <svg class="w-5 h-5 text-gray-400 group-open:rotate-180 transition-transform duration-300" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"></path></svg>
                            </summary>
                            <div class="p-5 border-t border-gray-200 space-y-5 bg-gray-50/30">
                                <div>
                                    <label class="block text-xs font-bold text-gray-500 uppercase tracking-wide mb-2">生效位置</label>
                                    <div class="flex gap-3">
                                        <label v-for="(label, val) in {1: '用户消息', 2: 'AI消息'}" :key="val"
                                            :class="['flex-1 flex items-center space-x-2 cursor-pointer p-2 rounded-xl border transition-all select-none shadow-sm active:scale-95', script.placement && script.placement.includes(Number(val)) ? 'bg-primary-50 border-primary-200 text-primary-700' : 'bg-white border-gray-100 text-gray-600 hover:border-primary-200']">
                                            <input type="checkbox" :checked="script.placement && script.placement.includes(Number(val))" @change="togglePlacement(Number(val))" class="hidden">
                                            <div :class="['w-4 h-4 rounded flex items-center justify-center border transition-colors', script.placement && script.placement.includes(Number(val)) ? 'bg-primary-600 border-primary-600' : 'bg-white border-gray-300']">
                                                <svg v-if="script.placement && script.placement.includes(Number(val))" class="w-3 h-3 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="4" d="M5 13l4 4L19 7"></path></svg>
                                            </div>
                                            <span class="text-xs font-bold">{{ label }}</span>
                                        </label>
                                    </div>
                                </div>

                                <div class="grid grid-cols-2 gap-3">
                                    <label v-for="(label, key) in {markdownOnly: '仅用户可见', promptOnly: '仅AI可见'}" :key="key"
                                        :class="['flex items-center space-x-2 cursor-pointer p-2 rounded-xl border transition-all select-none shadow-sm active:scale-95', script[key] ? 'bg-primary-50 border-primary-200 text-primary-700' : 'bg-white border-gray-100 text-gray-600 hover:border-primary-200']">
                                        <input type="checkbox" :checked="script[key]" @change="toggleMode(key, $event.target.checked)" class="hidden">
                                        <div :class="['w-4 h-4 rounded flex items-center justify-center border transition-colors', script[key] ? 'bg-primary-600 border-primary-600' : 'bg-white border-gray-300']">
                                            <svg v-if="script[key]" class="w-3 h-3 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="4" d="M5 13l4 4L19 7"></path></svg>
                                        </div>
                                        <span class="text-xs font-bold">{{ label }}</span>
                                    </label>
                                </div>

                                <div class="grid grid-cols-2 gap-4 pt-2 border-t border-gray-200/50">
                                    <div>
                                        <label class="block text-xs font-bold text-gray-500 uppercase tracking-wide mb-1.5">最小深度</label>
                                        <input type="number" :value="script.minDepth" @input="updateNumber('minDepth', $event.target.value)"
                                            class="w-full bg-white border border-gray-200 rounded-xl px-3 py-2 text-sm text-gray-800 focus:ring-4 focus:ring-primary-500/10 focus:border-primary-500 focus:outline-none transition-all shadow-sm" placeholder="无限制">
                                    </div>
                                    <div>
                                        <label class="block text-xs font-bold text-gray-500 uppercase tracking-wide mb-1.5">最大深度</label>
                                        <input type="number" :value="script.maxDepth" @input="updateNumber('maxDepth', $event.target.value)"
                                            class="w-full bg-white border border-gray-200 rounded-xl px-3 py-2 text-sm text-gray-800 focus:ring-4 focus:ring-primary-500/10 focus:border-primary-500 focus:outline-none transition-all shadow-sm" placeholder="无限制">
                                    </div>
                                </div>
                            </div>
                        </details>
                    </div>

                    <div class="p-4 md:p-5 border-t border-gray-100 flex justify-end space-x-3 bg-gray-50/80 backdrop-blur-sm flex-shrink-0">
                        <button @click="$emit('close')" class="modal-secondary-button">取消</button>
                        <button @click="$emit('save')" class="modal-primary-button">
                            <svg class="w-4 h-4 mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path></svg>
                            保存脚本
                        </button>
                    </div>
            </modal-shell>`
    };

    const UiTemplateEditorModal = {
        components: { CustomSelect },
        props: {
            show: Boolean,
            templateData: { type: Object, required: true },
            editing: Boolean,
            tab: { type: String, default: 'edit' },
            previewHtml: { type: String, default: '' },
            scopeOptions: { type: Array, default: () => [] },
            placementOptions: { type: Array, default: () => [] }
        },
        emits: ['close', 'save', 'update:tab', 'update-field'],
        methods: {
            updateField(field, value) {
                this.$emit('update-field', { field, value });
            },
            updateNumber(field, value) {
                this.updateField(field, value === '' ? '' : Number(value));
            },
            formatChangeValue(value) {
                if (value === undefined || value === null || value === '') return '空';
                if (typeof value !== 'object') return String(value);
                try {
                    return JSON.stringify(value, null, 2);
                } catch (error) {
                    return String(value);
                }
            }
        },
        template: `
            <modal-shell v-if="show" overlay-class="z-50 bg-black/50 backdrop-blur-sm p-2 md:p-3 animate-fade-in"
                panel-class="bg-white rounded-2xl border border-gray-200 w-full max-w-4xl flex flex-col shadow-2xl max-h-[94vh] overflow-hidden">
                    <modal-header @close="$emit('close')">
                        <div class="flex items-center gap-3">
                            <div class="p-2 bg-primary-50 text-primary-600 rounded-lg">
                                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 5a2 2 0 012-2h12a2 2 0 012 2v14a2 2 0 01-2 2H6a2 2 0 01-2-2V5zm4 3h8M8 12h8M8 16h5"></path></svg>
                            </div>
                            <div>
                                <h3 class="text-lg font-bold text-gray-800 leading-tight">{{ editing ? '编辑UI模板' : '新建UI模板' }}</h3>
                                <p class="text-xs text-gray-500">HTML状态栏 + 变量JSON</p>
                            </div>
                        </div>
                    </modal-header>

                    <div class="flex-1 overflow-y-auto custom-scrollbar p-6 bg-gray-50/30 space-y-6">
                        <div class="segmented-switch segmented-switch--slim">
                            <div class="segmented-switch__indicator" :class="{ 'is-right': tab !== 'history' }"></div>
                            <button @click="$emit('update:tab', 'history')" class="segmented-switch__option" :class="{ 'is-active': tab === 'history' }"><span>变更记录</span></button>
                            <button @click="$emit('update:tab', 'edit')" class="segmented-switch__option" :class="{ 'is-active': tab === 'edit' }"><span>编辑内容</span></button>
                        </div>

                        <div v-if="tab === 'history'" class="space-y-4">
                            <div v-if="!(templateData.changeLog || []).length" class="bg-white border border-dashed border-gray-200 rounded-2xl p-8 text-center text-gray-400">暂无变更记录</div>
                            <div v-else class="space-y-3">
                                <div v-for="log in (templateData.changeLog || []).slice(0, 1)" :key="log.id" class="bg-white border border-gray-200 rounded-2xl p-4 shadow-sm">
                                    <div class="mt-3 space-y-3">
                                        <div v-for="(change, key) in (log.changes || {})" :key="key" class="rounded-xl border border-gray-100 bg-gray-50/60 p-3">
                                            <div class="text-xs font-bold text-gray-700 mb-2">{{ key }}</div>
                                            <div class="grid grid-cols-1 md:grid-cols-2 gap-2">
                                                <div class="bg-white border border-gray-100 rounded-lg px-3 py-2 text-xs text-gray-600 leading-relaxed"><span class="font-bold text-gray-400">前：</span><span class="whitespace-pre-wrap break-words">{{ formatChangeValue(change && change.from) }}</span></div>
                                                <div class="bg-white border border-primary-100 rounded-lg px-3 py-2 text-xs text-gray-800 leading-relaxed"><span class="font-bold text-primary-500">后：</span><span class="whitespace-pre-wrap break-words">{{ formatChangeValue(change && change.to) }}</span></div>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div v-else class="space-y-6">
                            <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
                                <div>
                                    <label class="block text-xs font-bold text-gray-500 uppercase tracking-wide mb-1.5">模板名称</label>
                                    <input :value="templateData.name" @input="updateField('name', $event.target.value)" type="text"
                                        class="w-full bg-white border border-gray-200 rounded-xl px-4 py-2.5 text-gray-800 focus:ring-2 focus:ring-primary-500 focus:border-primary-500 focus:outline-none transition-all shadow-sm" placeholder="例如：角色状态栏">
                                </div>
                                <div>
                                    <label class="block text-xs font-bold text-gray-500 uppercase tracking-wide mb-1.5">作用范围</label>
                                    <custom-select :model-value="templateData.scope" @update:modelValue="updateField('scope', $event)" :options="scopeOptions"></custom-select>
                                </div>
                                <div>
                                    <label class="block text-xs font-bold text-gray-500 uppercase tracking-wide mb-1.5">插入位置</label>
                                    <custom-select :model-value="templateData.placement" @update:modelValue="updateField('placement', $event)" :options="placementOptions"></custom-select>
                                </div>
                                <div>
                                    <label class="block text-xs font-bold text-gray-500 uppercase tracking-wide mb-1.5">排序</label>
                                    <input :value="templateData.order" @input="updateNumber('order', $event.target.value)" type="number"
                                        class="w-full bg-white border border-gray-200 rounded-xl px-4 py-2.5 text-gray-800 focus:ring-2 focus:ring-primary-500 focus:border-primary-500 focus:outline-none transition-all shadow-sm" placeholder="100">
                                </div>
                            </div>

                            <details class="group bg-white border border-gray-200 rounded-xl shadow-sm overflow-hidden">
                                <summary class="list-none cursor-pointer select-none px-4 py-3 flex items-center justify-between gap-3 hover:bg-gray-50 transition-colors">
                                    <span class="text-xs font-bold text-gray-500 uppercase tracking-wide">HTML模板</span>
                                    <span class="flex items-center gap-2 text-[11px] font-bold text-gray-400">
                                        <span class="group-open:hidden">展开</span><span class="hidden group-open:inline">折叠</span>
                                        <svg class="w-4 h-4 transition-transform group-open:rotate-180" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"></path></svg>
                                    </span>
                                </summary>
                                <textarea :value="templateData.htmlTemplate" @input="updateField('htmlTemplate', $event.target.value)" rows="22"
                                    class="w-full bg-white border-0 border-t border-gray-100 rounded-none px-4 py-3 text-gray-800 focus:ring-2 focus:ring-inset focus:ring-primary-500 focus:outline-none font-mono text-sm shadow-inner leading-relaxed resize-y min-h-[460px]" placeholder="<section>...</section>"></textarea>
                            </details>
                            <div class="grid grid-cols-1 lg:grid-cols-2 gap-5">
                                <div>
                                    <label class="block text-xs font-bold text-gray-500 uppercase tracking-wide mb-1.5">变量JSON</label>
                                    <textarea :value="templateData.variableStateText" @input="updateField('variableStateText', $event.target.value)" rows="22"
                                        class="w-full bg-white border border-gray-200 rounded-xl px-4 py-3 text-gray-800 focus:ring-2 focus:ring-primary-500 focus:border-primary-500 focus:outline-none text-sm shadow-inner leading-relaxed resize-y min-h-[460px]" placeholder='{"status":"平稳","equipment":[{"slot":"武器","name":"短剑","durability":80}]}'></textarea>
                                </div>
                                <div>
                                    <label class="block text-xs font-bold text-gray-500 uppercase tracking-wide mb-1.5">变量说明（给AI参考，可选）</label>
                                    <textarea :value="templateData.variableSchemaText" @input="updateField('variableSchemaText', $event.target.value)" rows="22"
                                        class="w-full bg-white border border-gray-200 rounded-xl px-4 py-3 text-gray-800 focus:ring-2 focus:ring-primary-500 focus:border-primary-500 focus:outline-none text-sm shadow-inner leading-relaxed resize-y min-h-[460px]" placeholder="例如：status 表示角色当前身体和情绪状态；location 表示当前场景地点；relationship 表示双方关系变化。"></textarea>
                                </div>
                            </div>
                            <div>
                                <label class="block text-xs font-bold text-gray-500 uppercase tracking-wide mb-1.5">原始状态预览</label>
                                <div class="ui-template-preview w-full bg-white p-0 overflow-visible min-h-[460px]" v-html="previewHtml"></div>
                            </div>
                        </div>
                    </div>

                    <div v-if="tab === 'edit'" class="p-4 md:p-5 border-t border-gray-100 flex justify-end space-x-3 bg-gray-50/80 backdrop-blur-sm flex-shrink-0">
                        <button @click="$emit('close')" class="modal-secondary-button">取消</button>
                        <button @click="$emit('save')" class="modal-primary-button">
                            <svg class="w-4 h-4 mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path></svg>
                            保存模板
                        </button>
                    </div>
            </modal-shell>`
    };

    const WorldInfoEditorModal = {
        components: { CustomSelect },
        props: {
            show: Boolean,
            entry: { type: Object, required: true },
            editing: Boolean,
            keysText: { type: String, default: '' },
            scopeOptions: { type: Array, default: () => [] },
            positionOptions: { type: Array, default: () => [] }
        },
        emits: ['close', 'save', 'update-field', 'update-keys'],
        methods: {
            updateField(field, value) {
                this.$emit('update-field', { field, value });
            },
            updateNumber(field, value) {
                this.updateField(field, value === '' ? '' : Number(value));
            },
            updateRegexMode(checked) {
                this.updateField('useRegex', checked);
                this.$emit('update-keys', this.keysText);
            }
        },
        template: `
            <modal-shell v-if="show" overlay-class="z-50 bg-black/50 backdrop-blur-sm p-2 md:p-3 animate-fade-in"
                panel-class="bg-white rounded-2xl border border-gray-200 w-full max-w-3xl flex flex-col shadow-2xl max-h-[94vh] overflow-hidden">
                    <modal-header @close="$emit('close')">
                        <div class="flex items-center gap-3">
                            <div class="p-2 bg-primary-50 text-primary-600 rounded-lg">
                                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"></path></svg>
                            </div>
                            <div>
                                <h3 class="text-lg font-bold text-gray-800 leading-tight">{{ editing ? '编辑世界书' : '新建世界书' }}</h3>
                                <p class="text-xs text-gray-500">世界书条目</p>
                            </div>
                        </div>
                    </modal-header>

                    <div class="flex-1 p-6 space-y-6 bg-gray-50/30 overflow-y-auto custom-scrollbar">
                        <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
                            <div class="space-y-4">
                                <div>
                                    <label class="block text-xs font-bold text-gray-500 uppercase tracking-wide mb-1.5">名称/备注 (Comment)</label>
                                    <input :value="entry.comment" @input="updateField('comment', $event.target.value)" type="text"
                                        class="w-full bg-white border border-gray-200 rounded-xl px-4 py-2.5 text-gray-800 focus:ring-2 focus:ring-primary-500 focus:border-primary-500 focus:outline-none transition-all shadow-sm font-medium" placeholder="例如：主城描述">
                                </div>
                                <div>
                                    <label class="block text-xs font-bold text-gray-500 uppercase tracking-wide mb-1.5">作用范围</label>
                                    <custom-select :model-value="entry.scope" @update:modelValue="updateField('scope', $event)" :options="scopeOptions"></custom-select>
                                </div>
                                <div>
                                    <label class="block text-xs font-bold text-gray-500 uppercase tracking-wide mb-1.5">主关键词 (Keys)</label>
                                    <input :value="keysText" @input="$emit('update-keys', $event.target.value)" type="text"
                                        class="w-full bg-white border border-gray-200 rounded-xl px-4 py-2.5 text-gray-800 focus:ring-2 focus:ring-primary-500 focus:border-primary-500 focus:outline-none transition-all shadow-sm" placeholder="逗号分隔，留空则需勾选'常驻'">
                                    <div v-if="entry.keys && entry.keys.length" class="mt-2 flex flex-wrap gap-1.5">
                                        <span v-for="(key, keyIndex) in entry.keys" :key="'wi-key-' + keyIndex + '-' + key" :title="key"
                                            class="inline-flex items-center max-w-full rounded-xl border border-primary-100 bg-primary-50 px-2.5 py-1.5 text-xs font-bold text-primary-700 shadow-sm">
                                            <span :class="entry.useRegex ? 'break-all whitespace-normal leading-relaxed' : 'truncate'">{{ key }}</span>
                                        </span>
                                    </div>
                                </div>
                                <div class="flex flex-wrap gap-2">
                                    <label :class="['flex-1 flex items-center justify-center space-x-1.5 cursor-pointer px-3 py-1.5 border rounded-xl transition-all select-none shadow-sm active:scale-95', entry.useRegex ? 'bg-primary-50 border-primary-200 text-primary-700' : 'bg-white border-gray-200 text-gray-600 hover:border-primary-300']">
                                        <input type="checkbox" :checked="entry.useRegex" @change="updateRegexMode($event.target.checked)" class="hidden">
                                        <svg v-if="entry.useRegex" class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M5 13l4 4L19 7"></path></svg>
                                        <span class="text-xs font-bold">正则匹配</span>
                                    </label>
                                    <label :class="['flex-1 flex items-center justify-center space-x-1.5 cursor-pointer px-3 py-1.5 border rounded-xl transition-all select-none shadow-sm active:scale-95', entry.constant ? 'bg-primary-50 border-primary-200 text-primary-700' : 'bg-white border-gray-200 text-gray-600 hover:border-primary-300']" title="常驻条目无需关键词触发，启用后始终插入">
                                        <input type="checkbox" :checked="entry.constant" @change="updateField('constant', $event.target.checked)" class="hidden">
                                        <svg v-if="entry.constant" class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M5 13l4 4L19 7"></path></svg>
                                        <span class="text-xs font-bold">始终常驻</span>
                                    </label>
                                </div>
                            </div>

                            <div class="bg-white border border-gray-200 rounded-xl p-4 shadow-sm flex flex-col justify-between">
                                <div class="space-y-4">
                                    <div>
                                        <label class="block text-xs font-bold text-gray-500 uppercase tracking-wide mb-1.5">插入位置</label>
                                        <custom-select :model-value="entry.position" @update:modelValue="updateField('position', $event)" :options="positionOptions"
                                            button-class="bg-white text-sm focus:ring-4 focus:ring-primary-500/10" menu-class="text-sm"></custom-select>
                                    </div>
                                    <div class="grid grid-cols-2 gap-3">
                                        <div>
                                            <label class="block text-xs text-gray-500 mb-1">顺序</label>
                                            <input type="number" :value="entry.order" @input="updateNumber('order', $event.target.value)"
                                                class="w-full bg-white border border-gray-200 rounded-lg px-3 py-1.5 text-sm focus:ring-2 focus:ring-primary-500 focus:outline-none" placeholder="100">
                                        </div>
                                        <div>
                                            <label class="block text-xs font-bold text-gray-500 uppercase tracking-wide mb-1.5">触发概率 (%)</label>
                                            <div :class="['flex items-center border rounded-xl transition-all overflow-hidden shadow-sm', entry.useProbability ? 'border-primary-300 ring-2 ring-primary-500/10' : 'border-gray-200 opacity-60']">
                                                <button @click="updateField('useProbability', !entry.useProbability)"
                                                    :class="['px-3 py-2 transition-colors border-r', entry.useProbability ? 'bg-primary-600 text-white border-primary-600' : 'bg-gray-100 text-gray-400 border-gray-200']">
                                                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M13 10V3L4 14h7v7l9-11h-7z"></path></svg>
                                                </button>
                                                <input type="number" :value="entry.probability" @input="updateNumber('probability', $event.target.value)" min="0" max="100"
                                                    class="w-full bg-white px-3 py-1.5 text-sm font-bold text-gray-700 focus:outline-none" :disabled="!entry.useProbability" placeholder="100">
                                            </div>
                                        </div>
                                    </div>
                                    <div>
                                        <label class="block text-xs font-bold text-gray-500 uppercase tracking-wide mb-1.5">自定义扫描深度</label>
                                        <input type="number" :value="entry.scanDepth" @input="updateNumber('scanDepth', $event.target.value)"
                                            class="w-full bg-white border border-gray-200 rounded-lg px-3 py-1.5 text-sm focus:ring-2 focus:ring-primary-500 focus:outline-none" placeholder="默认">
                                    </div>
                                    <div v-if="entry.position === 'at_depth'" class="pt-2 border-t border-gray-100">
                                        <label class="block text-xs text-gray-500 mb-1">插入深度 <span class="text-[10px] text-gray-400">@D</span></label>
                                        <input type="number" :value="entry.depth" @input="updateNumber('depth', $event.target.value)"
                                            class="w-full bg-white border border-gray-200 rounded-lg px-3 py-1.5 text-sm focus:ring-2 focus:ring-primary-500 focus:outline-none" placeholder="4">
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div>
                            <label class="block text-xs font-bold text-gray-500 uppercase tracking-wide mb-1.5 flex justify-between">
                                <span>内容</span>
                                <span class="text-[10px] font-normal normal-case bg-gray-100 px-1.5 rounded text-gray-500">{{ (entry.content || '').length }} 字符</span>
                            </label>
                            <textarea :value="entry.content" @input="updateField('content', $event.target.value)" rows="12"
                                class="w-full bg-white border border-gray-200 rounded-xl px-4 py-3 text-gray-800 focus:ring-2 focus:ring-primary-500 focus:border-primary-500 focus:outline-none text-sm shadow-inner leading-relaxed resize-y min-h-[260px]" placeholder="在此输入世界书条目的具体内容..."></textarea>
                        </div>
                    </div>

                    <div class="p-4 md:p-5 border-t border-gray-100 flex justify-end space-x-3 bg-gray-50/80 backdrop-blur-sm flex-shrink-0 wi-footer">
                        <button @click="$emit('close')" class="modal-secondary-button">取消</button>
                        <button @click="$emit('save')" class="modal-primary-button">
                            <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path></svg>
                            保存条目
                        </button>
                    </div>
            </modal-shell>`
    };

    const ExportSelectionModal = {
        props: {
            show: Boolean,
            items: { type: Array, default: () => [] },
            selected: { type: Set, required: true }
        },
        emits: ['close', 'select-all', 'deselect-all', 'toggle', 'confirm'],
        template: `
            <modal-shell v-if="show" overlay-class="z-[90] bg-black/50 backdrop-blur-sm p-4 animate-fade-in"
                panel-class="bg-white rounded-xl border border-gray-200 w-full max-w-lg flex flex-col shadow-2xl max-h-[80vh] overflow-hidden">
                    <div class="p-4 border-b border-gray-100 flex justify-between items-center bg-gray-50/80 backdrop-blur-sm flex-shrink-0">
                        <h3 class="text-lg font-bold text-gray-800">选择导出项目</h3>
                        <button @click="$emit('close')" class="text-gray-400 hover:text-gray-600 hover:bg-gray-100 p-1 rounded-full transition-all">
                            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>
                        </button>
                    </div>
                    <div class="p-2 border-b border-gray-100 flex justify-between items-center bg-white">
                        <button @click="$emit('select-all')" class="px-3 py-1.5 text-xs font-bold text-primary-600 hover:bg-primary-50 rounded-lg transition-colors">全选</button>
                        <span class="text-xs text-gray-500">已选: {{ selected.size }}</span>
                        <button @click="$emit('deselect-all')" class="px-3 py-1.5 text-xs font-bold text-gray-500 hover:bg-gray-100 rounded-lg transition-colors">取消全选</button>
                    </div>
                    <div class="flex-1 overflow-y-auto custom-scrollbar p-2 space-y-2">
                        <div v-for="(item, index) in items" :key="index" @click="$emit('toggle', index)"
                            class="flex items-center p-4 rounded-lg border cursor-pointer transition-all select-none"
                            :class="selected.has(index) ? 'bg-primary-50 border-primary-200' : 'bg-white border-gray-200 hover:border-primary-300'">
                            <div class="flex-shrink-0 mr-4">
                                <div :class="['w-6 h-6 rounded border flex items-center justify-center transition-colors', selected.has(index) ? 'bg-primary-600 border-primary-600' : 'bg-white border-gray-300']">
                                    <svg v-if="selected.has(index)" class="w-4 h-4 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M5 13l4 4L19 7"></path>
                                    </svg>
                                </div>
                            </div>
                            <div class="flex-1 min-w-0">
                                <div class="font-medium text-gray-900 truncate text-base">{{ item.name || item.comment || '未命名' }}</div>
                            </div>
                        </div>
                    </div>
                    <div class="p-4 border-t border-gray-100 flex justify-end space-x-3 bg-gray-50/80 backdrop-blur-sm flex-shrink-0">
                        <button @click="$emit('close')" class="modal-secondary-button">取消</button>
                        <button @click="$emit('confirm')" :disabled="selected.size === 0"
                            class="px-6 py-2.5 bg-primary-600 hover:bg-primary-700 text-white rounded-xl transition-all shadow-md hover:shadow-lg font-bold text-sm active:scale-95 flex items-center disabled:opacity-50 disabled:cursor-not-allowed">
                            <svg class="w-4 h-4 mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12"></path></svg>
                            导出选中 ({{ selected.size }})
                        </button>
                    </div>
            </modal-shell>`
    };

    const CharacterExportModal = {
        props: { show: Boolean },
        emits: ['close', 'export'],
        data: () => ({
            options: [
                { type: 'json', title: '导出为 JSON 文件', description: '导出角色卡数据 .json', path: 'M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12' },
                { type: 'png', title: '导出为 PNG 文件', description: '导出带头像图片的角色卡 .png', path: 'M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12' },
                { type: 'chat', title: '导出聊天记录', description: '一次性导出当前角色的全部分支聊天 .jsonl', path: 'M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z' }
            ]
        }),
        template: `
            <modal-shell v-if="show" close-on-backdrop @close="$emit('close')"
                overlay-class="z-[90] bg-black/50 backdrop-blur-sm p-4 animate-fade-in"
                panel-class="compact-modal-panel">
                    <div class="p-6">
                        <h3 class="text-xl font-bold text-gray-900 mb-6 flex items-center">
                            <svg class="w-6 h-6 mr-2 text-primary-600" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12"></path></svg>
                            导出选项
                        </h3>
                        <div class="grid grid-cols-1 gap-3">
                            <button v-for="option in options" :key="option.type" @click="$emit('export', option.type)" class="choice-card">
                                <div class="choice-card__icon">
                                    <svg class="w-6 h-6 text-primary-600" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" :d="option.path"></path></svg>
                                </div>
                                <div class="flex-1">
                                    <div class="font-bold text-base mb-0.5">{{ option.title }}</div>
                                    <div class="text-[11px] text-gray-500">{{ option.description }}</div>
                                </div>
                            </button>
                        </div>
                        <button @click="$emit('close')" class="mt-6 w-full py-3 text-red-500 font-medium hover:text-red-600 transition-colors">取消</button>
                    </div>
            </modal-shell>`
    };

    const ActionConfirmModal = {
        props: {
            show: Boolean,
            message: { type: String, default: '' }
        },
        emits: ['confirm', 'cancel'],
        template: `
            <modal-shell v-if="show" overlay-class="z-[160] bg-black/50 backdrop-blur-sm p-4 animate-fade-in"
                panel-class="bg-white rounded-xl border border-gray-200 w-full max-w-sm flex flex-col shadow-2xl transform transition-all scale-100">
                    <div class="p-6 text-center">
                        <div class="mx-auto flex items-center justify-center h-12 w-12 rounded-full bg-red-100 mb-4">
                            <svg class="h-6 w-6 text-red-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"></path>
                            </svg>
                        </div>
                        <h3 class="text-lg font-medium text-gray-900 mb-2">确认操作</h3>
                        <p class="text-sm text-gray-500 whitespace-pre-wrap" v-html="message"></p>
                    </div>
                    <div class="bg-gray-50 px-4 py-3 sm:px-6 sm:flex sm:flex-row-reverse rounded-b-xl">
                        <button @click="$emit('confirm')" type="button" class="w-full inline-flex justify-center rounded-lg border border-transparent shadow-sm px-4 py-2 bg-red-600 text-base font-medium text-white hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-red-500 sm:ml-3 sm:w-auto sm:text-sm transition-colors">确认</button>
                        <button @click="$emit('cancel')" type="button" class="mt-3 w-full inline-flex justify-center rounded-lg border border-gray-300 shadow-sm px-4 py-2 bg-white text-base font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary-500 sm:mt-0 sm:ml-3 sm:w-auto sm:text-sm transition-colors">取消</button>
                    </div>
            </modal-shell>`
    };

    const RetryConfirmModal = {
        props: { state: { type: Object, required: true } },
        template: `
            <transition enter-active-class="transition duration-300 ease-modal-fade" enter-from-class="opacity-0"
                enter-to-class="opacity-100" leave-active-class="transition duration-200 ease-in"
                leave-from-class="opacity-100" leave-to-class="opacity-0">
                <modal-shell v-if="state.show" overlay-class="z-[200] bg-black/40 backdrop-blur-[2px] px-4 pt-4 pb-20 text-center sm:p-0"
                    panel-class="bg-white rounded-2xl shadow-[0_20px_60px_-10px_rgba(0,0,0,0.15)] transform transition-transform w-full max-w-sm overflow-hidden relative z-10 border border-gray-100 p-6 flex flex-col items-center animate-slide-up">
                        <div class="w-12 h-12 rounded-full bg-yellow-50 flex items-center justify-center mb-4 border border-yellow-100 shadow-sm">
                            <svg class="w-6 h-6 text-yellow-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"></path>
                            </svg>
                        </div>
                        <h3 class="text-lg font-bold text-gray-900 mb-2 tracking-tight">{{ state.title }}</h3>
                        <p class="text-[13px] text-gray-500 mb-6 whitespace-pre-wrap leading-relaxed px-2">{{ state.message }}</p>
                        <div class="flex space-x-3 w-full">
                            <button @click="state.onCancel" class="flex-1 py-2.5 px-4 bg-gray-50 hover:bg-gray-100 text-gray-600 font-bold rounded-xl transition-all border border-gray-200 shadow-sm focus:outline-none focus:ring-2 focus:ring-gray-300 hover:-translate-y-0.5">取消中断</button>
                            <button @click="state.onConfirm" class="flex-1 py-2.5 px-4 bg-primary-600 hover:bg-primary-500 text-white font-bold rounded-xl shadow-md hover:shadow-lg transition-all focus:outline-none focus:ring-2 focus:ring-primary-400 hover:-translate-y-0.5">立即重试</button>
                        </div>
                </modal-shell>
            </transition>`
    };

    const ContextViewerModal = {
        props: {
            show: Boolean,
            floors: { type: Number, default: 0 },
            totalLength: { type: Number, default: 0 },
            worldInfos: { type: Array, default: () => [] },
            messages: { type: Array, default: () => [] }
        },
        emits: ['close'],
        template: `
            <transition enter-active-class="transition-opacity duration-300 ease-modal-fade" enter-from-class="opacity-0"
                enter-to-class="opacity-100" leave-active-class="transition-opacity duration-200 ease-in"
                leave-from-class="opacity-100" leave-to-class="opacity-0">
                <modal-shell v-if="show" close-on-backdrop @close="$emit('close')"
                    overlay-class="z-[100] bg-gray-900/40 backdrop-blur-sm p-4 sm:p-6"
                    panel-class="bg-white rounded-2xl shadow-2xl w-full max-w-4xl flex flex-col overflow-hidden max-h-[90vh] sm:max-h-[85vh] border border-gray-200/50 relative">
                        <div class="px-6 py-4 border-b border-gray-100 flex justify-between items-center bg-gray-50/50 flex-shrink-0">
                            <div class="flex items-center space-x-3">
                                <div class="text-blue-600">
                                    <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path></svg>
                                </div>
                                <div>
                                    <h3 class="text-lg font-bold text-gray-800 pr-10">真实上下文请求</h3>
                                    <p class="mt-0.5 text-xs font-medium text-gray-500">共 {{ floors }} 楼 · 总字数 {{ Number(totalLength || 0).toLocaleString() }}</p>
                                </div>
                            </div>
                            <button @click="$emit('close')" class="text-gray-400 hover:text-red-500 transition-colors bg-gray-100 hover:bg-red-50 p-2 rounded-full absolute top-4 right-4 z-50">
                                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>
                            </button>
                        </div>

                        <div class="p-5 overflow-y-auto flex-1 space-y-3 bg-gray-50 custom-scrollbar overscroll-contain relative">
                            <details class="group bg-blue-50/80 border border-blue-200/60 rounded-xl shadow-sm mb-4">
                                <summary class="font-bold text-blue-800 flex items-center p-4 text-sm cursor-pointer select-none outline-none">
                                    <svg class="w-4 h-4 mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"></path></svg>
                                    <span>本次插入的世界书 (共 {{ worldInfos.length }} 项)</span>
                                    <div class="ml-auto flex items-center">
                                        <svg class="w-4 h-4 text-blue-500 group-open:rotate-180 transition-transform duration-300" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"></path></svg>
                                    </div>
                                </summary>
                                <div class="px-4 pb-4 pt-0">
                                    <div class="flex flex-wrap gap-2 max-h-32 overflow-y-auto custom-scrollbar">
                                        <div v-for="(wi, index) in worldInfos" :key="'wi-' + index" class="bg-blue-100 text-blue-700 border-blue-200 px-2.5 py-1.5 rounded-md text-xs shadow-sm border flex flex-col justify-center">
                                            <span class="font-bold pb-0.5">{{ wi.name }}</span>
                                            <span v-if="wi.triggers" class="text-blue-600/90 border-blue-200/50 font-normal text-[10px] mt-0.5 pt-0.5 border-t leading-none">{{ wi.triggers === '常驻' ? '常驻' : wi.name && wi.name.startsWith('角色记忆') ? wi.triggers : '触发: ' + wi.triggers }}</span>
                                        </div>
                                        <span v-if="worldInfos.length === 0" class="text-blue-500/80 text-sm italic">未触发任何世界书或世界书功能关闭。</span>
                                    </div>
                                </div>
                            </details>

                            <div class="space-y-3 pb-4">
                                <div v-for="(message, index) in messages" :key="index" class="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden transition-all hover:shadow-md group/msg">
                                    <details class="group">
                                        <summary class="flex flex-col p-3.5 bg-gray-50/50 hover:bg-gray-100/50 cursor-pointer select-none transition-colors gap-2">
                                            <div class="flex flex-row justify-between items-center w-full">
                                                <div class="flex items-center gap-2">
                                                    <span :class="{
                                                        'bg-green-100 text-green-700 border border-green-200 shadow-sm': message.isMemory,
                                                        'bg-red-100 text-red-700 border border-red-200 shadow-sm': message.role === 'system' && !message.isMemory,
                                                        'bg-green-100 text-green-700 border border-green-200 shadow-sm': message.role === 'user',
                                                        'bg-purple-100 text-purple-700 border border-purple-200 shadow-sm': message.role === 'assistant'
                                                    }" class="px-2.5 py-1 rounded-md text-[11px] font-black uppercase tracking-wider flex items-center justify-center min-w-[70px] whitespace-nowrap">
                                                        <span v-if="message.floor" class="opacity-70 mr-1 font-bold">F{{ message.floor }}</span> {{ message.isMemory ? '记忆' : message.role }}
                                                    </span>
                                                    <span class="font-bold bg-white border border-gray-200 shadow-sm text-gray-500 px-2.5 rounded-full py-0.5 text-[11px]">{{ message.content.length }} 字符</span>
                                                </div>
                                                <div class="flex items-center flex-shrink-0 ml-2">
                                                    <svg class="w-4 h-4 text-gray-400 group-open:rotate-180 transition-transform duration-300" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"></path></svg>
                                                </div>
                                            </div>
                                            <div v-if="message.wiTriggers && message.wiTriggers.length" class="flex flex-wrap gap-1.5 items-center w-full">
                                                <div v-for="(trigger, triggerIndex) in message.wiTriggers" :key="triggerIndex" class="bg-blue-100 text-blue-700 border-blue-200 px-2 py-0.5 rounded flex flex-col border">
                                                    <span class="text-[11px] font-semibold tracking-wide">{{ trigger.name }}</span>
                                                    <span v-if="trigger.triggers" class="text-blue-600/90 border-blue-200/50 text-[9px] font-normal mt-[1px] pt-[1px] border-t leading-[10px]">{{ trigger.triggers === '常驻' ? '常驻' : trigger.name && trigger.name.startsWith('角色记忆') ? trigger.triggers : '触发: ' + trigger.triggers }}</span>
                                                </div>
                                            </div>
                                        </summary>
                                        <div class="p-4 bg-white border-t border-gray-100 text-[13px] text-gray-700 whitespace-pre-wrap leading-relaxed max-h-[500px] overflow-y-auto custom-scrollbar decoration-clone selection:bg-blue-200 selection:text-blue-900 break-words" v-html="message.renderedContent"></div>
                                    </details>
                                </div>

                                <div v-if="messages.length === 0" class="flex flex-col items-center justify-center py-12 text-gray-400">
                                    <svg class="w-12 h-12 mb-3 text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path></svg>
                                    <p>暂无上下文记录，请先执行一次生成请求。</p>
                                </div>
                            </div>
                        </div>
                </modal-shell>
            </transition>`
    };

    const TokenUsageView = {
        components: { PaginationControls, SettingsHelp, SettingsPageHeader },
        props: {
            show: Boolean,
            historyLength: { type: Number, default: 0 },
            filter: { type: String, default: 'all' },
            timeFilter: { type: String, default: 'all' },
            showTimeFilter: Boolean,
            timeFilterLabel: { type: String, default: '' },
            timeFilterOptions: { type: Array, default: () => [] },
            stats: { type: Object, default: () => ({}) },
            filteredCount: { type: Number, default: 0 },
            records: { type: Array, default: () => [] },
            page: { type: Number, default: 1 },
            pageCount: { type: Number, default: 1 },
            helpTopic: { type: String, default: '' },
            formatAggregate: { type: Function, required: true },
            formatCount: { type: Function, required: true },
            formatTime: { type: Function, required: true },
            getTypeLabel: { type: Function, required: true },
            getUncachedInput: { type: Function, required: true }
        },
        emits: [
            'menu', 'clear', 'update:filter', 'update:time-filter', 'update:show-time-filter',
            'update:page', 'update:help-topic'
        ],
        setup() {
            const formatDuration = (value) => {
                if (!Number.isFinite(value)) return '--';
                if (value < 1000) return `${Math.round(value)}ms`;
                return `${Number((value / 1000).toFixed(1))}s`;
            };
            const formatOutputSpeed = (record) => {
                if (record?.isStream !== true
                    || !Number.isFinite(record?.durationMs) || record.durationMs <= 0
                    || !Number.isFinite(record?.outputCharacters) || record.outputCharacters <= 0) return '--';
                return `${Math.round(record.outputCharacters * 1000 / record.durationMs)}字/s`;
            };
            return {
                formatDuration,
                formatOutputSpeed,
                formatQuota: quota => `¥${(Math.trunc(quota / 500000 * 10000) / 10000).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 4 })}`,
                filterOptions: Object.freeze([
                    { value: 'all', label: '全部', position: '' },
                    { value: 'chat', label: '主对话', position: 'is-position-2' },
                    { value: 'memory', label: '记忆系统', position: 'is-position-3' },
                    { value: 'variables', label: '变量分析', position: 'is-position-4' }
                ])
            };
        },
        template: `
            <div v-if="show" class="management-view">
                <settings-page-header title="用量统计" @menu="$emit('menu')">
                    <template #icon>
                        <svg class="w-6 h-6 md:w-7 md:h-7 mr-2 text-primary-600" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 19V9m5 10V5m5 14v-7m5 7V3M3 21h18"></path>
                        </svg>
                    </template>
                    <button v-if="historyLength > 0" @click="$emit('clear')"
                        class="flex-shrink-0 rounded-xl border border-gray-200 bg-white p-2.5 text-red-600 shadow-sm transition-all hover:border-red-100 hover:bg-red-50 active:scale-95"
                        title="清空记录" aria-label="清空 Token 用量记录">
                        <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path>
                        </svg>
                    </button>
                </settings-page-header>

                <div class="mb-4 flex items-center gap-2">
                    <div class="segmented-switch segmented-switch--compact segmented-switch--four min-w-0 flex-1">
                        <div class="segmented-switch__indicator" :class="filterOptions.find(option => option.value === filter)?.position"></div>
                        <button v-for="option in filterOptions" :key="option.value" type="button"
                            @click="$emit('update:filter', option.value)" class="segmented-switch__option"
                            :class="{ 'is-active': filter === option.value }">{{ option.label }}</button>
                    </div>
                    <div class="token-usage-time-filter-container relative flex-none">
                        <button type="button" @click="$emit('update:show-time-filter', !showTimeFilter)"
                            class="flex h-10 w-10 items-center justify-center rounded-xl border shadow-sm transition-all active:scale-95"
                            :class="timeFilter === 'all' ? 'border-gray-200 bg-white text-gray-500 hover:bg-gray-50 hover:text-gray-700' : 'border-primary-200 bg-primary-50 text-primary-600'"
                            :title="'时间范围：' + timeFilterLabel" aria-label="筛选 Token 记录时间范围" :aria-expanded="showTimeFilter">
                            <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 5h16l-6 7v5l-4 2v-7L4 5z"></path>
                            </svg>
                        </button>
                        <transition enter-active-class="transition duration-150 ease-out" enter-from-class="opacity-0 translate-y-1 scale-95"
                            enter-to-class="opacity-100 translate-y-0 scale-100" leave-active-class="transition duration-100 ease-in"
                            leave-from-class="opacity-100 translate-y-0 scale-100" leave-to-class="opacity-0 translate-y-1 scale-95">
                            <div v-if="showTimeFilter" class="absolute right-0 top-full z-30 mt-2 w-36 origin-top-right rounded-xl border border-gray-200 bg-white p-1.5 shadow-xl ring-1 ring-black/5">
                                <button v-for="option in timeFilterOptions" :key="option.value" type="button"
                                    @click="$emit('update:time-filter', option.value); $emit('update:show-time-filter', false)"
                                    class="flex w-full items-center justify-between rounded-lg px-3 py-2 text-sm font-medium transition-colors"
                                    :class="timeFilter === option.value ? 'bg-primary-50 text-primary-700' : 'text-gray-600 hover:bg-gray-50 hover:text-gray-800'">
                                    <span>{{ option.label }}</span>
                                    <svg v-if="timeFilter === option.value" class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path>
                                    </svg>
                                </button>
                            </div>
                        </transition>
                    </div>
                </div>

                <div class="relative mb-6 flex items-center justify-between gap-4 rounded-2xl border border-gray-200 bg-white px-5 py-4 shadow-sm">
                    <div class="flex min-w-0 items-center text-sm font-semibold text-gray-500"><span>总用量</span>
                        <settings-help topic="totalTokens" :open-topic="helpTopic" label="查看总用量说明" icon-class=""
                            popover-class="token-usage-help-popover" @toggle="$emit('update:help-topic', $event)">
                            汇总当前类型和时间筛选范围内，输入 Token（包括缓存读取）与输出 Token 的总和。
                        </settings-help>
                    </div>
                    <div class="flex-shrink-0 whitespace-nowrap font-mono text-xl font-bold tabular-nums text-gray-900">{{ formatAggregate(stats.inputTokens + stats.cacheReadTokens + stats.outputTokens, stats.inputTokensReports + stats.cacheReadTokensReports + stats.outputTokensReports) }}</div>
                </div>

                <div class="flex items-center justify-between mb-3">
                    <h3 class="text-sm font-bold text-gray-700">请求日志</h3>
                    <span class="text-[11px] text-gray-400">共 {{ filteredCount }} 条</span>
                </div>
                <div v-if="records.length > 0" class="space-y-3">
                    <article v-for="record in records" :key="record.id"
                        class="rounded-2xl border border-gray-200 bg-white p-4 shadow-sm transition-colors hover:border-gray-300">
                        <div class="mb-3 min-w-0">
                            <div class="flex min-w-0 items-center justify-between gap-3">
                                <span class="min-w-0 flex-1 truncate text-sm text-gray-600" :title="record.model">{{ record.model || '未知模型' }}</span>
                                 <span class="flex-shrink-0 text-sm font-semibold text-gray-500">{{ getTypeLabel(record.type) }}</span>
                            </div>
                            <div class="mt-1.5 flex min-w-0 items-center justify-between gap-3">
                                <div class="flex min-w-0 items-center gap-3 text-xs text-gray-400">
                                    <span>耗时 {{ formatDuration(record.durationMs) }}</span>
                                    <span v-if="record.isStream === true">速度 {{ formatOutputSpeed(record) }}</span>
                                </div>
                                <time class="flex-shrink-0 text-xs text-gray-400">{{ formatTime(record.timestamp) }}</time>
                            </div>
                        </div>
                        <div class="space-y-1.5 rounded-xl border border-gray-100 bg-gray-50/60 px-3 py-2.5">
                            <div class="flex min-w-0 items-center justify-between gap-3 whitespace-nowrap">
                                <span class="inline-flex items-center gap-1.5 text-xs font-semibold text-gray-500">
                                    <span class="h-1.5 w-1.5 rounded-full bg-primary-500"></span>输入
                                </span>
                                <span class="flex min-w-0 items-center gap-1 font-mono">
                                    <span class="text-sm font-bold text-gray-800">{{ formatCount(getUncachedInput(record)) }}</span>
                                    <span v-if="Number(record.cacheReadTokens) > 0"
                                        class="inline-flex min-w-0 items-center gap-0.5 text-sm font-bold text-gray-500/80"
                                        title="缓存读取">
                                        <svg class="h-4 w-4 flex-none" fill="none" stroke="currentColor" aria-hidden="true"><use href="#icon-arrow-down"></use></svg>
                                        {{ formatCount(record.cacheReadTokens) }}
                                    </span>
                                </span>
                            </div>
                            <div class="flex min-w-0 items-center justify-between gap-3 whitespace-nowrap">
                                <span class="inline-flex items-center gap-1.5 text-xs font-semibold text-gray-500">
                                    <span class="h-1.5 w-1.5 rounded-full bg-yellow-400"></span>输出
                                </span>
                                <span class="font-mono text-sm font-bold text-gray-800">{{ formatCount(record.outputTokens) }}</span>
                            </div>
                            <div v-if="Number.isFinite(record.actualQuota)" class="flex min-w-0 items-center justify-between gap-3 whitespace-nowrap">
                                <span class="inline-flex items-center gap-1.5 text-xs font-semibold text-gray-500">
                                    <span class="h-1.5 w-1.5 rounded-full bg-green-500"></span>消耗
                                </span>
                                <span class="font-mono text-sm font-bold text-gray-800" :title="record.usageGroup ? '计费分组：' + record.usageGroup : ''">{{ formatQuota(record.actualQuota) }}</span>
                            </div>
                        </div>
                    </article>
                    <pagination-controls :current="page" :total="pageCount" label="Token 记录分页" @change="$emit('update:page', $event)"></pagination-controls>
                </div>
                <div v-else class="rounded-2xl border border-dashed border-gray-200 bg-white/60 py-12 text-center">
                    <div class="text-sm font-medium text-gray-500">{{ historyLength > 0 ? '当前分类还没有记录' : '还没有 Token 用量记录' }}</div>
                    <div class="mt-1 text-xs text-gray-400">{{ historyLength > 0 ? '可以切换其他分类查看' : '完成一次 API 请求后会显示在这里' }}</div>
                </div>
            </div>`
    };

    const UiTemplatesView = {
        components: { SettingsHelp, SettingsPageHeader },
        props: {
            show: Boolean,
            templates: { type: Array, default: () => [] },
            hasCharacter: Boolean,
            showSettings: Boolean,
            settings: { type: Object, required: true },
            updateStatus: { type: Object, required: true },
            helpTopic: String,
            analysisDepth: Number
        },
        emits: [
            'menu', 'export', 'import', 'create', 'update:show-settings', 'update:help-topic',
            'analyze', 'select-model', 'update:analysis-depth', 'edit', 'delete'
        ],
        template: `
            <div v-if="show" class="management-view">
                <settings-page-header title="UI模板" @menu="$emit('menu')">
                    <template #icon>
                        <svg class="w-6 h-6 md:w-7 md:h-7 mr-2 text-primary-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 5a2 2 0 012-2h12a2 2 0 012 2v14a2 2 0 01-2 2H6a2 2 0 01-2-2V5zm4 3h8M8 12h8M8 16h5"></path>
                        </svg>
                    </template>
                    <button @click="$emit('export')" :disabled="templates.length === 0"
                        class="settings-icon-button disabled:opacity-40" title="导出">
                        <svg class="w-5 h-5" fill="none" stroke="currentColor"><use href="#icon-export"></use></svg>
                    </button>
                    <label class="settings-icon-button cursor-pointer" title="导入">
                        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><use href="#icon-import"></use></svg>
                        <input type="file" accept=".json" @change="$emit('import', $event)" class="hidden">
                    </label>
                    <button @click="$emit('create')" class="settings-create-button" title="新建模板">
                        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"></path>
                        </svg>
                    </button>
                </settings-page-header>

                <div class="bg-white/70 backdrop-blur-sm p-1 rounded-2xl border border-gray-200 shadow-sm mb-4 overflow-hidden">
                    <button @click="$emit('update:show-settings', !showSettings); $emit('update:help-topic', '')"
                        class="settings-collapse-trigger" aria-controls="ui-template-settings-panel"
                        :aria-expanded="showSettings"
                        :class="['w-full flex justify-between items-center px-4 py-3 rounded-xl font-bold',
                            showSettings ? 'bg-primary-50 text-primary-700' : 'text-gray-700 hover:bg-gray-50']">
                        <span class="flex items-center min-w-0">
                            <span :class="['p-1.5 rounded-lg mr-3 transition-colors', showSettings ? 'bg-primary-100 text-primary-600' : 'bg-gray-100 text-gray-500']">
                                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                        d="M4 5a2 2 0 012-2h12a2 2 0 012 2v14a2 2 0 01-2 2H6a2 2 0 01-2-2V5zm4 3h8M8 12h8M8 16h5"></path>
                                </svg>
                            </span>
                            <span class="truncate">变量系统设置</span>
                            <span v-if="updateStatus.state !== 'idle'"
                                class="ml-2 inline-flex items-center text-[11px] font-bold px-2 py-0.5 rounded-full border"
                                :class="{
                                    'bg-blue-50 text-blue-700 border-blue-200': updateStatus.state === 'running',
                                    'bg-green-50 text-green-700 border-green-200': updateStatus.state === 'success',
                                    'bg-yellow-50 text-yellow-700 border-yellow-200': updateStatus.state === 'skipped',
                                    'bg-red-50 text-red-700 border-red-200': updateStatus.state === 'error',
                                    'bg-gray-50 text-gray-500 border-gray-200': updateStatus.state === 'idle'
                                }">
                                {{ updateStatus.message }}
                            </span>
                        </span>
                        <span class="flex items-center gap-3 flex-shrink-0">
                            <span class="text-xs font-bold" :class="settings.uiTemplateEnabled ? 'text-primary-600' : 'text-gray-400'">
                                {{ settings.uiTemplateEnabled ? '已开启' : '已关闭' }}
                            </span>
                            <svg :class="{'transform rotate-180': showSettings}"
                                class="settings-collapse-chevron w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"></path>
                            </svg>
                        </span>
                    </button>
                    <div id="ui-template-settings-panel" class="settings-collapse"
                        :class="{ 'is-open': showSettings }" :aria-hidden="!showSettings" :inert="!showSettings">
                        <div class="settings-collapse__inner">
                            <div class="settings-collapse__content px-4 pb-4 pt-3 border-t border-gray-100">
                                <div class="flex items-center justify-between mb-4">
                                    <div class="flex items-center space-x-3">
                                        <span class="text-sm font-bold text-gray-700">变量分析</span>
                                        <label class="relative inline-flex items-center cursor-pointer">
                                            <input type="checkbox" v-model="settings.uiTemplateEnabled" class="settings-toggle-input sr-only">
                                            <div class="settings-toggle settings-toggle--compact"></div>
                                        </label>
                                    </div>
                                    <div class="flex items-center justify-end gap-2 min-h-[30px]">
                                        <button @click="$emit('analyze')" :disabled="!settings.uiTemplateEnabled"
                                            class="inline-flex items-center text-xs px-3 py-1.5 bg-white hover:bg-primary-50 text-primary-700 rounded-lg border border-primary-200 transition-all duration-200 disabled:opacity-40 disabled:cursor-not-allowed font-medium active:scale-95"
                                            :class="settings.uiTemplateMainModelAnalysis ? 'invisible opacity-0 pointer-events-none' : 'visible opacity-100'">
                                            <svg class="w-3.5 h-3.5 mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"></path>
                                            </svg>
                                            <span>立即分析</span>
                                        </button>
                                    </div>
                                </div>
                                <div v-if="settings.uiTemplateEnabled" class="mb-3 animate-fade-in">
                                    <div class="segmented-switch segmented-switch--compact w-full">
                                        <div class="segmented-switch__indicator" :class="{ 'is-right': !settings.uiTemplateMainModelAnalysis }"></div>
                                        <button type="button" @click="settings.uiTemplateMainModelAnalysis = true"
                                            class="segmented-switch__option" :class="{ 'is-active': settings.uiTemplateMainModelAnalysis }">主模型</button>
                                        <button type="button" @click="settings.uiTemplateMainModelAnalysis = false"
                                            class="segmented-switch__option" :class="{ 'is-active': !settings.uiTemplateMainModelAnalysis }">副模型</button>
                                    </div>
                                </div>
                                <div v-if="settings.uiTemplateEnabled && !settings.uiTemplateMainModelAnalysis"
                                    class="flex items-center justify-between py-4 border-t border-gray-100 animate-fade-in">
                                    <div class="flex items-center space-x-3">
                                        <span class="text-sm font-bold text-gray-700">将变量注入上下文</span>
                                        <label class="relative inline-flex items-center cursor-pointer">
                                            <input type="checkbox" v-model="settings.uiTemplateInjectContext" class="settings-toggle-input sr-only">
                                            <div class="settings-toggle settings-toggle--compact"></div>
                                        </label>
                                    </div>
                                </div>
                                <div v-if="settings.uiTemplateEnabled && !settings.uiTemplateMainModelAnalysis" class="settings-fields animate-fade-in">
                                    <div class="settings-field">
                                        <div class="relative mb-1.5 flex items-center">
                                            <label class="text-sm font-semibold text-gray-700">分析模型</label>
                                            <settings-help topic="analysisModel" :open-topic="helpTopic"
                                                label="查看分析模型说明" @toggle="$emit('update:help-topic', $event)">
                                                仅在使用副模型分析时生效。它负责阅读对话并更新 UI 模板变量，不参与正文回复。模型速度会影响分析等待时间，结构化输出能力会影响变量更新是否准确。
                                            </settings-help>
                                        </div>
                                        <div class="flex gap-2">
                                            <button @click="$emit('select-model')" class="settings-model-button truncate"
                                                :title="settings.uiTemplateModel || '未选择模型'">
                                                <span :class="settings.uiTemplateModel ? 'text-gray-700' : 'text-gray-400'">{{ settings.uiTemplateModel || '未选择模型' }}</span>
                                            </button>
                                        </div>
                                    </div>
                                    <div class="settings-field">
                                        <div class="flex justify-between items-center mb-2">
                                            <div class="relative flex items-center">
                                                <label class="text-sm font-semibold text-gray-700">分析对话层数</label>
                                                <settings-help topic="analysisDepth" :open-topic="helpTopic"
                                                    label="查看分析对话层数说明" popover-class="is-above"
                                                    @toggle="$emit('update:help-topic', $event)">
                                                    决定副模型分析时读取最近多少层对话。层数越多，能看到的剧情变化越完整，消耗越多；层数越少，消耗越少，但可能看不到较早发生的剧情
                                                </settings-help>
                                            </div>
                                            <span class="text-xs font-mono font-bold text-gray-600 bg-gray-50 border border-gray-200 rounded-lg px-2.5 py-1 whitespace-nowrap">
                                                {{ analysisDepth + ' 层' }}
                                            </span>
                                        </div>
                                        <input type="range" :value="analysisDepth"
                                            @input="$emit('update:analysis-depth', Number($event.target.value))"
                                            min="4" max="10" step="1"
                                            class="compact-range w-full h-1.5 bg-primary-100 rounded-lg appearance-none cursor-pointer accent-primary-500">
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <div v-if="!hasCharacter && templates.length === 0" class="py-24 text-center text-gray-400">请先选择角色卡</div>
                <div v-else-if="templates.length === 0" class="py-24 text-center text-gray-400">当前没有UI模板</div>
                <div v-else class="space-y-4">
                    <div v-for="(template, index) in templates" :key="template.id"
                        class="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
                        <div class="p-4 flex items-start justify-between gap-3">
                            <div class="min-w-0">
                                <div class="flex items-center gap-2 flex-wrap">
                                    <h3 class="font-bold text-gray-800 truncate">{{ template.name }}</h3>
                                    <span class="text-[10px] px-2 py-0.5 rounded-full border"
                                        :class="template.scope === 'global' ? 'bg-purple-50 text-purple-700 border-purple-200' : 'bg-yellow-50 text-yellow-700 border-yellow-200'">
                                        {{ template.scope === 'global' ? '全局' : '绑定' }}
                                    </span>
                                </div>
                                <p class="text-xs text-gray-500 mt-1">{{ Object.keys(template.variableState || {}).length }} 个变量 · {{ (template.changeLog || []).length }} 条记录</p>
                            </div>
                            <div class="flex items-center gap-1">
                                <label class="relative inline-flex items-center cursor-pointer mr-2">
                                    <input type="checkbox" v-model="template.enabled" class="settings-toggle-input sr-only">
                                    <div class="settings-toggle settings-toggle--compact settings-toggle--solid"></div>
                                </label>
                                <button @click="$emit('edit', index)" class="item-action-button item-action-button--edit" title="编辑">
                                    <svg class="w-5 h-5" fill="none" stroke="currentColor"><use href="#icon-edit"></use></svg>
                                </button>
                                <button @click="$emit('delete', index)" class="item-action-button item-action-button--delete" title="删除">
                                    <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                            d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path>
                                    </svg>
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>`
    };

    const StoryBranchModal = {
        props: {
            show: Boolean,
            currentBranch: Object,
            branches: { type: Array, default: () => [] },
            routeMap: { type: Object, default: () => ({ width: 0, height: 0, nodes: [], links: [] }) },
            dragging: Boolean,
            selectedNode: Object,
            switching: Boolean,
            canDelete: Boolean,
            showNameEditor: Boolean,
            nameDraft: { type: String, default: '' }
        },
        emits: [
            'close', 'start-drag', 'move-drag', 'end-drag', 'select-node', 'switch-branch',
            'open-name-editor', 'delete-branch', 'close-name-editor', 'update:name-draft', 'save-name'
        ],
        template: `
            <transition enter-active-class="transition-opacity duration-300 ease-out" enter-from-class="opacity-0"
                enter-to-class="opacity-100" leave-active-class="transition-opacity duration-200 ease-in"
                leave-from-class="opacity-100" leave-to-class="opacity-0">
                <modal-shell v-if="show" close-on-backdrop @close="$emit('close')"
                    overlay-class="z-[120] bg-gray-900/40 backdrop-blur-sm p-4 sm:p-6"
                    panel-class="w-full max-w-6xl h-[92vh] sm:h-[88vh] bg-white rounded-2xl shadow-2xl border border-gray-200/70 overflow-hidden flex flex-col">
                        <div class="px-5 sm:px-6 py-4 border-b border-gray-100 bg-white flex items-center justify-between flex-shrink-0">
                            <div class="min-w-0">
                                <div class="flex items-center gap-2.5">
                                    <div class="w-9 h-9 rounded-xl bg-blue-50 border border-blue-100 text-blue-600 flex items-center justify-center flex-shrink-0">
                                        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                            <use href="#icon-story-branch"></use>
                                        </svg>
                                    </div>
                                    <div class="min-w-0">
                                        <h3 class="text-lg font-bold text-gray-800">剧情分支</h3>
                                        <p class="text-xs text-gray-500 mt-0.5 truncate">当前分支：{{ currentBranch?.name || '主线' }}</p>
                                    </div>
                                </div>
                            </div>
                            <button @click="$emit('close')"
                                class="w-9 h-9 rounded-full bg-gray-50 hover:bg-red-50 text-gray-400 hover:text-red-500 flex items-center justify-center transition-colors flex-shrink-0">
                                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
                                </svg>
                            </button>
                        </div>

                        <div class="story-route-modal-body custom-scrollbar">
                            <section class="story-route-map-panel">
                                <div class="story-route-map-scroll custom-scrollbar"
                                    :class="{ 'is-single-route': branches.length === 1, 'is-dragging': dragging }"
                                    @pointerdown="$emit('start-drag', $event)" @pointermove="$emit('move-drag', $event)"
                                    @pointerup="$emit('end-drag', $event)" @pointercancel="$emit('end-drag', $event)"
                                    @lostpointercapture="$emit('end-drag', $event)" @dragstart.prevent>
                                    <div class="story-route-canvas"
                                        :style="{ width: routeMap.width + 'px', height: routeMap.height + 'px' }">
                                        <svg class="story-route-links" :width="routeMap.width" :height="routeMap.height"
                                            :viewBox="'0 0 ' + routeMap.width + ' ' + routeMap.height" aria-hidden="true">
                                            <path v-for="link in routeMap.links" :key="link.id" :d="link.path"
                                                class="story-route-link"
                                                :class="{ 'is-active': link.isActive, 'is-selected': link.isSelected }"></path>
                                        </svg>
                                        <button v-for="node in routeMap.nodes" :key="node.id"
                                            @click="$emit('select-node', node.id)" class="story-route-node"
                                            :class="{ 'is-current': node.isActive, 'is-selected': node.isSelected, 'is-on-route': node.isOnActiveRoute, 'is-on-selected-route': node.isOnSelectedRoute }"
                                            :style="{ left: node.x + 'px', top: node.y + 'px' }"
                                            :title="'选择分支：' + node.name">
                                            <span class="story-route-node-checkpoint" aria-hidden="true"></span>
                                            <span class="story-route-node-copy">
                                                <span v-if="node.id === 'main'" class="story-route-node-type">起点</span>
                                                <strong>{{ node.name }}</strong>
                                                <small>{{ node.floorCount }} 楼 · {{ node.wordCountText }} 字</small>
                                            </span>
                                            <span v-if="node.isActive" class="story-route-node-current">当前</span>
                                        </button>
                                    </div>
                                </div>
                            </section>

                            <div class="story-route-actions">
                                <button @click="$emit('switch-branch', selectedNode.id)"
                                    :disabled="switching || !selectedNode || selectedNode.isActive"
                                    class="story-route-enter-button"
                                    :title="selectedNode?.isActive ? '已在当前分支' : '进入当前选中的分支'">
                                    {{ switching ? '切换中' : '进入' }}
                                </button>
                                <button @click="$emit('open-name-editor')"
                                    :disabled="switching || !selectedNode || selectedNode.id === 'main'"
                                    class="story-route-edit-button"
                                    :title="!selectedNode ? '请先选择分支' : selectedNode.id === 'main' ? '主线名称不可修改' : '编辑当前选中的分支名称'">
                                    编辑
                                </button>
                                <button @click="$emit('delete-branch')" :disabled="switching || !canDelete"
                                    :title="canDelete ? '删除当前选中的分支' : '请选择可删除的分支（主线不可删除）'"
                                    class="story-route-delete-button">删除</button>
                            </div>
                        </div>
                </modal-shell>
            </transition>

            <transition enter-active-class="transition-opacity duration-200" enter-from-class="opacity-0"
                enter-to-class="opacity-100" leave-active-class="transition-opacity duration-150"
                leave-from-class="opacity-100" leave-to-class="opacity-0">
                <modal-shell v-if="showNameEditor" close-on-backdrop @close="$emit('close-name-editor')"
                    overlay-class="z-[180] bg-gray-900/40 backdrop-blur-sm p-4"
                    panel-class="w-full max-w-sm rounded-2xl border border-gray-200 bg-white p-5 shadow-2xl">
                        <h3 class="text-lg font-bold text-gray-800">编辑分支名称</h3>
                        <p class="mt-1 text-sm text-gray-500">名称最多 30 个字。</p>
                        <input :value="nameDraft" maxlength="30" autofocus
                            @input="$emit('update:name-draft', $event.target.value)"
                            @keyup.enter="$emit('save-name')" @keyup.esc="$emit('close-name-editor')"
                            class="mt-4 w-full rounded-xl border border-gray-200 bg-white px-4 py-3 text-sm font-medium text-gray-800 outline-none transition focus:border-blue-400 focus:ring-2 focus:ring-blue-100"
                            placeholder="输入分支名称">
                        <div class="mt-5 flex justify-end gap-2">
                            <button @click="$emit('close-name-editor')"
                                class="rounded-lg border border-gray-200 bg-white px-4 py-2 text-sm font-bold text-gray-600 transition hover:bg-gray-50">取消</button>
                            <button @click="$emit('save-name')" :disabled="switching"
                                class="rounded-lg bg-blue-600 px-4 py-2 text-sm font-bold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50">保存</button>
                        </div>
                </modal-shell>
            </transition>`
    };

    const CharacterCard = {
        props: {
            char: { type: Object, required: true },
            mobile: Boolean,
            active: Boolean,
            loading: Boolean,
            batchMode: Boolean,
            selected: Boolean,
            favorite: Boolean,
            worldInfoCount: { type: Number, default: 0 },
            regexCount: { type: Number, default: 0 }
        },
        emits: ['select', 'edit', 'export-card', 'toggle-favorite', 'delete-card'],
        setup() {
            let pressStartedAt = 0;
            let releaseTimer = null;
            let cleanupTimer = null;
            let coverZoomStartedAt = 0;
            let coverZoomTimer = null;

            const clearCardTimers = () => {
                clearTimeout(releaseTimer);
                clearTimeout(cleanupTimer);
                clearTimeout(coverZoomTimer);
            };
            const beginCoverZoom = (event) => {
                if (event.pointerType !== 'mouse') return;
                clearTimeout(coverZoomTimer);
                const card = event.currentTarget;
                if (!card.classList.contains('is-cover-zoomed')) coverZoomStartedAt = performance.now();
                card.classList.add('is-cover-zoomed');
            };
            const endCoverZoom = (event) => {
                if (event.pointerType !== 'mouse') return;
                const card = event.currentTarget;
                clearTimeout(coverZoomTimer);
                coverZoomTimer = setTimeout(() => card.classList.remove('is-cover-zoomed'),
                    Math.max(0, 400 - (performance.now() - coverZoomStartedAt)));
            };
            const beginPress = (event) => {
                clearCardTimers();
                const card = event.currentTarget;
                card.classList.remove('is-card-releasing');
                card.classList.add('is-card-pressing');
                pressStartedAt = performance.now();
            };
            const endPress = (event) => {
                const card = event.currentTarget;
                if (!card.classList.contains('is-card-pressing')) return;
                clearTimeout(releaseTimer);
                releaseTimer = setTimeout(() => {
                    card.classList.remove('is-card-pressing');
                    card.classList.add('is-card-releasing');
                    cleanupTimer = setTimeout(() => card.classList.remove('is-card-releasing'), 180);
                }, Math.max(0, 120 - (performance.now() - pressStartedAt)));
            };

            onBeforeUnmount(clearCardTimers);
            return { beginCoverZoom, endCoverZoom, beginPress, endPress };
        },
        template: `
            <div class="char-grid-item relative rounded-2xl overflow-hidden transition-[transform,shadow,border-color] duration-300"
                :class="mobile
                    ? ['aspect-[2/3] shadow-md border border-gray-100', active && !batchMode ? 'ring-4 ring-primary-500 ring-offset-2' : '']
                    : ['bg-white border border-gray-200 hover:border-primary-400 hover:shadow-xl cursor-pointer group shadow-sm flex flex-col', active && !batchMode ? 'ring-4 ring-primary-500 ring-offset-2' : '', batchMode && selected ? 'ring-2 ring-red-500 border-red-500' : '']"
                :aria-busy="loading"
                @pointerenter="beginCoverZoom" @pointerdown="beginPress" @pointerup="endPress"
                @pointercancel="endPress" @pointerleave="endPress($event); endCoverZoom($event)"
                @click="!loading && $emit('select')">
                <div v-if="loading && !batchMode" @click.stop role="status" aria-live="polite"
                    class="absolute inset-0 z-40 flex items-center justify-center bg-gray-950/45 backdrop-blur-[2px]">
                    <div class="flex flex-col items-center gap-3 text-white drop-shadow-md">
                        <svg class="generated-image-spinner character-switch-spinner" viewBox="0 0 50 50" aria-hidden="true">
                            <circle class="generated-image-spinner-path" cx="25" cy="25" r="20" fill="none" stroke-width="3"></circle>
                        </svg>
                        <span class="text-sm font-semibold tracking-wide">正在切换</span>
                    </div>
                </div>
                <template v-if="mobile">
                    <img :src="char?.avatar" class="absolute inset-0 w-full h-full object-cover" loading="lazy" decoding="async">
                    <div class="absolute inset-0 bg-gradient-to-t from-black/80 via-black/20 to-transparent"></div>

                    <div v-if="batchMode" class="absolute inset-0 bg-black/40 flex items-center justify-center z-20">
                        <div class="w-10 h-10 rounded-full border-2 flex items-center justify-center transition-colors"
                            :class="selected ? 'bg-red-500 border-red-500' : 'bg-white/20 border-white/50'">
                            <svg v-if="selected" class="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path>
                            </svg>
                        </div>
                    </div>

                    <div v-if="active && !batchMode" class="absolute top-3 left-3 z-10">
                        <div class="flex items-center text-[10px] font-bold text-white bg-green-600/60 backdrop-blur-xl px-2 py-1 rounded-full border border-white/30 shadow-lg">
                            <span class="w-1.5 h-1.5 bg-green-400 rounded-full mr-1.5 shadow-[0_0_5px_rgba(74,222,128,0.8)]"></span>
                            当前使用
                        </div>
                    </div>

                    <div v-if="!batchMode" class="absolute top-3 right-3 flex flex-col gap-2 z-20">
                        <button @click.stop="$emit('edit')"
                            class="p-2 bg-white/20 backdrop-blur-md text-white rounded-full border border-white/20 active:bg-white/40 shadow-lg">
                            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"></path>
                            </svg>
                        </button>
                        <button @click.stop="$emit('export-card')"
                            class="p-2 bg-white/20 backdrop-blur-md text-white rounded-full border border-white/20 active:bg-white/40 shadow-lg">
                            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12"></path>
                            </svg>
                        </button>
                        <button @click.stop="$emit('toggle-favorite')"
                            class="p-2 bg-white/20 backdrop-blur-md rounded-full border border-white/20 active:bg-white/40 shadow-lg transition-colors"
                            :class="favorite ? 'text-amber-300' : 'text-white'"
                            :title="favorite ? '取消收藏' : '收藏角色'" :aria-label="favorite ? '取消收藏' : '收藏角色'">
                            <svg class="w-4 h-4" :fill="favorite ? 'currentColor' : 'none'" stroke="currentColor" viewBox="0 0 24 24"><use href="#icon-star"></use></svg>
                        </button>
                    </div>

                    <div class="absolute bottom-0 left-0 right-0 p-3 z-10">
                        <h3 class="text-white font-bold text-sm truncate mb-2 drop-shadow-md">{{ char.name }}</h3>
                        <div v-if="!batchMode" class="flex items-center justify-between">
                            <div class="flex flex-wrap gap-1">
                                <span class="px-1.5 py-0.5 bg-white/20 backdrop-blur-md text-white text-[8px] rounded border border-white/10">{{ worldInfoCount }} 世界书</span>
                                <span class="px-1.5 py-0.5 bg-white/20 backdrop-blur-md text-white text-[8px] rounded border border-white/10">{{ regexCount }} 正则</span>
                            </div>
                        </div>
                    </div>
                </template>

                <template v-else>
                    <div class="aspect-w-2 aspect-h-3 relative h-[500px] overflow-hidden">
                        <img :src="char?.avatar" class="character-card-cover w-full h-full object-cover" loading="lazy" decoding="async">
                        <div class="absolute inset-0 bg-gradient-to-t from-gray-900 via-gray-900/20 to-transparent opacity-60 group-hover:opacity-80 transition-opacity"></div>

                        <div v-if="active && !batchMode" class="absolute top-4 left-4 z-10">
                            <div class="flex items-center text-xs font-bold text-white bg-green-600/60 backdrop-blur-xl px-3 py-1.5 rounded-full border border-white/30 shadow-[0_8px_20px_-4px_rgba(0,0,0,0.3)]">
                                <span class="w-2 h-2 bg-green-400 rounded-full mr-2 animate-pulse shadow-[0_0_8px_rgba(74,222,128,0.8)]"></span>
                                当前使用
                            </div>
                        </div>

                        <div v-if="!batchMode" class="absolute top-3 right-3 flex flex-col gap-2 opacity-0 group-hover:opacity-100 transition-opacity duration-300">
                            <button @click.stop="$emit('edit')" class="p-2 bg-white/90 backdrop-blur-sm text-gray-700 hover:text-primary-600 rounded-full shadow-lg transition-all hover:scale-110" title="编辑角色">
                                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"></path>
                                </svg>
                            </button>
                            <button @click.stop="$emit('export-card')" class="p-2 bg-white/90 backdrop-blur-sm text-gray-700 hover:text-primary-600 rounded-full shadow-lg transition-all hover:scale-110" title="导出角色">
                                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12"></path>
                                </svg>
                            </button>
                            <button @click.stop="$emit('toggle-favorite')" class="p-2 bg-white/90 backdrop-blur-sm rounded-full shadow-lg transition-all hover:scale-110"
                                :class="favorite ? 'text-amber-500 hover:text-amber-600' : 'text-gray-700 hover:text-amber-500'"
                                :title="favorite ? '取消收藏' : '收藏角色'" :aria-label="favorite ? '取消收藏' : '收藏角色'">
                                <svg class="w-4 h-4" :fill="favorite ? 'currentColor' : 'none'" stroke="currentColor" viewBox="0 0 24 24"><use href="#icon-star"></use></svg>
                            </button>
                            <button @click.stop="$emit('delete-card')" class="p-2 bg-white/90 backdrop-blur-sm text-red-500 hover:text-red-700 rounded-full shadow-lg transition-all hover:scale-110" title="删除角色">
                                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path>
                                </svg>
                            </button>
                        </div>

                        <div class="absolute bottom-0 left-0 p-4 w-full">
                            <h3 class="text-xl font-bold text-white mb-1 truncate drop-shadow-lg">{{ char.name }}</h3>
                            <div class="flex items-center gap-2">
                                <span class="px-2 py-0.5 bg-white/20 backdrop-blur-md text-white text-[10px] rounded-md border border-white/10">{{ worldInfoCount }} 世界书</span>
                                <span class="px-2 py-0.5 bg-white/20 backdrop-blur-md text-white text-[10px] rounded-md border border-white/10">{{ regexCount }} 正则</span>
                            </div>
                        </div>

                        <div v-if="batchMode" class="absolute inset-0 bg-black/40 flex items-center justify-center transition-opacity"
                            :class="selected ? 'opacity-100' : 'opacity-0 group-hover:opacity-100'">
                            <div class="w-14 h-14 rounded-full bg-white flex items-center justify-center shadow-2xl transform transition-transform"
                                :class="selected ? 'scale-100' : 'scale-75'">
                                <div class="w-12 h-12 rounded-full border-2 flex items-center justify-center"
                                    :class="selected ? 'bg-red-500 border-red-500' : 'border-gray-300'">
                                    <svg v-if="selected" class="w-8 h-8 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path>
                                    </svg>
                                </div>
                            </div>
                        </div>
                    </div>
                </template>
            </div>`
    };

    window.RPHubComponents = {
        ActionConfirmModal,
        ActiveToolEditorModal,
        AddCharacterModal,
        AutoImageGenModal,
        CharacterExportModal,
        CharacterEditorModal,
        CharacterCard,
        ContextViewerModal,
        EmbeddedViewContent,
        GenerationTimer,
        ExportSelectionModal,
        ModelSelectorModal,
        ModalHeader,
        ModalShell,
        PaginationControls,
        PresetEditorModal,
        RegexEditorModal,
        RetryConfirmModal,
        SettingsHelp,
        SettingsPageHeader,
        StatusNoticeModal,
        StoryBranchModal,
        TokenUsageView,
        UiTemplatesView,
        UiTemplateEditorModal,
        UpdateNotificationModal,
        UserSetupModal,
        UiTemplatePending,
        WorldInfoEditorModal
    };
})();
