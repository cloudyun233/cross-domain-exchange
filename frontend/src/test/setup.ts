import '@testing-library/jest-dom/vitest';
import { afterEach, beforeEach, vi } from 'vitest';

vi.mock('antd', async () => {
  const React = await vi.importActual<typeof import('react')>('react');
  const h = React.createElement;
  const wrap = (tag: string, extra: Record<string, any> = {}) =>
    ({ children, className, style, ...props }: any) => h(tag, { className, style, ...extra, ...props }, children);
  const Button = ({ children, htmlType, type, icon, loading, disabled, className = '', block, danger, size, ...props }: any) =>
    h('button', {
      ...props,
      disabled: disabled || loading,
      type: htmlType || 'button',
      className: `${className} ${type === 'primary' ? 'ant-btn-primary' : ''}`.trim(),
    }, icon, children);
  const Input = ({ prefix, onPressEnter, onKeyDown, ...props }: any) =>
    h('input', {
      ...props,
      onKeyDown: (event: any) => {
        onKeyDown?.(event);
        if (event.key === 'Enter') onPressEnter?.(event);
      },
    });
  Input.Password = (props: any) => h('input', { ...props, type: 'password' });
  Input.TextArea = (props: any) => h('textarea', props);
  const defaultFormValues = {
    username: 'alice',
    password: 'secret',
    roleType: 'admin',
    domainId: 1,
    domainCode: 'domain',
    domainName: 'Domain',
    status: 1,
    topicFilter: 'cross/#',
    action: 'all',
    accessType: 'allow',
  };
  const Form = ({ children, onFinish, form, ...props }: any) => {
    if (form) form.__onFinish = onFinish;
    return h('form', {
      ...props,
      onSubmit: (event: Event) => {
        event.preventDefault();
        onFinish?.(defaultFormValues);
      },
    }, children);
  };
  Form.Item = ({ children }: any) => h(React.Fragment, null, children);
  Form.useForm = () => {
    const form: any = {
    resetFields: vi.fn(),
    setFieldsValue: vi.fn(),
      submit: vi.fn(() => form.__onFinish?.(defaultFormValues)),
    };
    return [form];
  };
  Form.useWatch = () => undefined;
  const Layout = wrap('div');
  Layout.Header = wrap('header');
  Layout.Sider = wrap('aside');
  Layout.Content = wrap('main');
  const Typography = {
    Title: ({ level = 1, children, ...props }: any) => h(`h${level}`, props, children),
    Text: ({ children, strong, type, ...props }: any) => h('span', props, children),
    Paragraph: ({ children, type, ...props }: any) => h('p', props, children),
  };
  const Tag = ({ children, color, closable, onClose, ...props }: any) =>
    h('span', { ...props, 'data-color': color }, children,
      closable ? h('button', { type: 'button', onClick: onClose, 'aria-label': 'close' }, 'x') : null);
  const Space = ({ children }: any) => h('div', null, children);
  const Card = ({ children, title, extra, ...props }: any) => h('section', props, title, extra, children);
  const Table = ({ dataSource = [], columns = [], rowClassName, pagination }: any) =>
    h('div', null,
      dataSource.map((record: any, rowIndex: number) =>
        h('div', { key: record.id || rowIndex, className: rowClassName?.(record) },
          columns.map((column: any, colIndex: number) =>
            h('span', { key: column.dataIndex || colIndex },
              column.render ? column.render(record[column.dataIndex], record) : record[column.dataIndex])))),
      pagination?.showTotal ? h('div', null, pagination.showTotal(pagination.total || 0)) : null,
      pagination?.onChange ? h('button', { type: 'button', onClick: () => pagination.onChange((pagination.current || 1) + 1) }, 'next-page') : null);
  const flattenTree = (nodes: any[]): any[] => nodes.flatMap((node) => [node, ...flattenTree(node.children || [])]);
  const Tree = ({ treeData = [], onSelect, titleRender }: any) =>
    h('div', null, flattenTree(treeData).map((node) =>
      h('div', {
        key: node.key,
        role: 'treeitem',
        onClick: () => onSelect?.([node.key], { node }),
      }, titleRender ? titleRender(node) : node.title)));
  const Select = ({ options = [], onChange, value, placeholder }: any) =>
    h('select', { value: value ?? '', 'aria-label': placeholder, onChange: (event: any) => onChange?.(event.target.value || undefined) },
      h('option', { value: '' }, placeholder || ''),
      options.map((option: any) => h('option', { key: option.value, value: option.value }, option.label)));
  const Modal = ({ children, open, onOk, onCancel, title }: any) =>
    open ? h('div', { className: 'ant-modal' }, h('h2', null, title), children,
      h('button', { type: 'button', onClick: onOk }, 'ok'),
      h('button', { type: 'button', onClick: onCancel }, 'cancel')) : null;
  const Popconfirm = ({ children, onConfirm }: any) =>
    h('span', { onClick: onConfirm }, children);
  const RadioGroup = ({ children, onChange }: any) =>
    h('div', { onClick: (event: any) => onChange?.({ target: { value: event.target.value } }) }, children);
  const RadioButton = ({ children, value }: any) => h('button', { type: 'button', value }, children);
  const Radio = { Group: RadioGroup, Button: RadioButton };
  const Checkbox = ({ children, checked, onChange }: any) =>
    h('label', null, h('input', { type: 'checkbox', checked, onChange }), children);
  const List = ({ dataSource = [], renderItem }: any) =>
    h('div', null, dataSource.map((item: any, index: number) => h('div', { key: index }, renderItem(item))));
  List.Item = wrap('div');

  return {
    Alert: wrap('div'),
    Badge: ({ text }: any) => h('span', null, text),
    Button,
    Card,
    Checkbox,
    Col: wrap('div'),
    ConfigProvider: ({ children }: any) => h(React.Fragment, null, children),
    Dropdown: ({ children, menu }: any) => h(React.Fragment, null,
      children,
      menu?.items?.map((item: any) =>
        h('button', { key: item.key, type: 'button', onClick: item.onClick }, item.label))),
    Empty: ({ description }: any) => h('div', null, description),
    Form,
    Input,
    Layout,
    List,
    Menu: ({ items = [], onClick }: any) => h('nav', null, items.map((item: any) =>
      item.type === 'divider' ? h('hr', { key: Math.random() }) : h('button', { key: item.key, type: 'button', onClick: () => onClick?.({ key: item.key }) }, item.label))),
    Modal,
    Popconfirm,
    Radio,
    Result: ({ title, subTitle }: any) => h('div', null, title, subTitle),
    Row: wrap('div'),
    Select,
    Space,
    Spin: () => h('div', { className: 'ant-spin' }, 'loading'),
    Statistic: ({ title, value, suffix }: any) => h('div', null, title, value, suffix),
    Table,
    Tag,
    Tooltip: ({ children }: any) => h(React.Fragment, null, children),
    Tree,
    Typography,
    message: {
      success: vi.fn(),
      error: vi.fn(),
      warning: vi.fn(),
      info: vi.fn(),
    },
  };
});

Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
});

Object.defineProperty(window, 'getComputedStyle', {
  value: () => ({
    getPropertyValue: () => '',
  }),
});

Object.defineProperty(window, 'ResizeObserver', {
  writable: true,
  value: vi.fn().mockImplementation(function ResizeObserverMock(this: any) {
    return {
    observe: vi.fn(),
    unobserve: vi.fn(),
    disconnect: vi.fn(),
    };
  }),
});

Object.defineProperty(window.HTMLCanvasElement.prototype, 'getContext', {
  value: vi.fn(),
});

Object.defineProperty(URL, 'createObjectURL', {
  writable: true,
  value: vi.fn(() => 'blob:mock-url'),
});

Object.defineProperty(URL, 'revokeObjectURL', {
  writable: true,
  value: vi.fn(),
});

Object.defineProperty(window.HTMLAnchorElement.prototype, 'click', {
  configurable: true,
  value: vi.fn(),
});

beforeEach(() => {
  sessionStorage.clear();
  vi.restoreAllMocks();
  vi.clearAllMocks();
});

afterEach(() => {
  vi.useRealTimers();
});
